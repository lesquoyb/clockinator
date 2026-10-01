package com.example.alarmcontroller.ui

import android.net.Uri
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.AlarmRepository
import com.example.alarmcontroller.alarm.AlarmRingtoneService
import com.example.alarmcontroller.alarm.AlarmScheduler
import com.example.alarmcontroller.alarm.Recurrence
import com.example.alarmcontroller.alarm.RingtoneSlot
import com.example.alarmcontroller.alarm.RingtoneType
import com.example.alarmcontroller.alarm.nfc.NfcActionHandler
import com.example.alarmcontroller.communication.ConnectionState
import com.example.alarmcontroller.communication.DeviceCommunicator
import com.example.alarmcontroller.communication.DeviceMessage
import com.example.alarmcontroller.communication.MessageDirection
import com.example.alarmcontroller.communication.SyncPayload
import com.example.alarmcontroller.communication.ble.BleDeviceItem
import com.example.alarmcontroller.communication.ble.BleManager
import com.example.alarmcontroller.lock.LockModeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Base64
import java.io.InputStream
import java.util.zip.CRC32

enum class CommunicatorType { BLE, WIFI }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager      = BleManager(application)
    private val alarmRepository = AlarmRepository(application)
    private val alarmScheduler  = AlarmScheduler(application)
    private val nfcHandler      = NfcActionHandler(application)

    val lockModeManager: LockModeManager = nfcHandler.lockModeManager
    val isSetupRequired: Boolean = alarmRepository.isSetupRequired()

    fun completeSetup() {
        alarmRepository.markSetupCompleted()
    }

    // Focus-mode onboarding
    private val _showFocusOnboarding = MutableStateFlow(!alarmRepository.isFocusOnboardingDone())
    val showFocusOnboarding: StateFlow<Boolean> = _showFocusOnboarding.asStateFlow()

    fun dismissFocusOnboarding() {
        alarmRepository.markFocusOnboardingDone()
        _showFocusOnboarding.value = false
    }

    fun dndSettingsIntent()  = lockModeManager.dndSettingsIntent()
    fun focusModeIntent()    = lockModeManager.focusModeIntent()

    private var activeCommunicator: DeviceCommunicator = bleManager

    val connectionState:  StateFlow<ConnectionState>     = bleManager.connectionState
    val scannedDevices:   StateFlow<List<BleDeviceItem>> = bleManager.scannedDevices
    val isScanning:       StateFlow<Boolean>             = bleManager.isScanning
    val isBluetoothEnabled: Boolean get() = bleManager.isBluetoothEnabled

    private val _messages = MutableStateFlow<List<DeviceMessage>>(emptyList())
    val messages: StateFlow<List<DeviceMessage>> = _messages.asStateFlow()

    private val _statusAlert = MutableStateFlow<String?>(null)
    val statusAlert: StateFlow<String?> = _statusAlert.asStateFlow()

    private val _nfcToastMessages = Channel<String>(Channel.BUFFERED)
    val nfcToastMessages = _nfcToastMessages.receiveAsFlow()

    private val _alarms = MutableStateFlow<List<AlarmItem>>(emptyList())
    val alarms: StateFlow<List<AlarmItem>> = _alarms.asStateFlow()

    val isPhoneAlarmRinging: StateFlow<Boolean> = AlarmRingtoneService.isRinging
    val ringingAlarmTitle:   StateFlow<String?> = AlarmRingtoneService.ringingAlarmTitle

    // ------------------------------------------------------------------
    // Upload state
    // ------------------------------------------------------------------

    sealed interface UploadState {
        data object Idle : UploadState
        data class InProgress(val filename: String, val progress: Float) : UploadState
        data class Success(val filename: String) : UploadState
        data class Error(val message: String) : UploadState
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val _uploadedRingtones = MutableStateFlow(alarmRepository.getUploadedRingtones())
    val uploadedRingtones: StateFlow<Set<String>> = _uploadedRingtones.asStateFlow()

    // Internal channel: BLE ACK strings routed here from the message collector
    private val _bleAckFlow = MutableSharedFlow<String>(extraBufferCapacity = 32)

    // ------------------------------------------------------------------
    // Sync queue
    // ------------------------------------------------------------------

    private val _hasPendingSync = MutableStateFlow(false)
    val hasPendingSync: StateFlow<Boolean> = _hasPendingSync.asStateFlow()

    private val _autoSyncHours = MutableStateFlow(alarmRepository.getAutoSyncHours())
    val autoSyncHours: StateFlow<Int> = _autoSyncHours.asStateFlow()

    // ------------------------------------------------------------------
    // init
    // ------------------------------------------------------------------

    init {
        loadAlarms()

        // 1. Collect incoming BLE messages
        viewModelScope.launch {
            bleManager.incomingMessages.collect { message ->
                _messages.update { it + message }
                if (message.direction == MessageDirection.RX) {
                    handleIncomingMessage(message.text.trim())
                }
            }
        }

        // 2. On reconnect: flush pending sync
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    alarmRepository.saveLastBleMac(state.address)
                    delay(1_000)
                    pushSyncToDevice()
                    _hasPendingSync.value = false
                }
            }
        }

        // 3. Register autoConnect watch on startup
        viewModelScope.launch {
            val mac = alarmRepository.getLastBleMac()
            if (mac != null && bleManager.isBluetoothEnabled) {
                delay(1_500)
                bleManager.reconnectInBackground(mac)
            }
        }

        // 4. Periodic auto-sync
        viewModelScope.launch {
            while (true) {
                val hours = _autoSyncHours.value
                if (hours > 0) {
                    delay(hours * 3_600_000L)
                    if (connectionState.value is ConnectionState.Connected) pushSyncToDevice()
                    else _hasPendingSync.value = true
                } else {
                    delay(60_000L)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Incoming BLE message router
    // ------------------------------------------------------------------

    /**
     * Routes a raw text string received from the ESP32.
     * The ESP can send:
     *   - JSON: STOP_ALARM, SNOOZE_ALARM, DISMISS_NEXT
     *   - Plain text: SNOOZE:<minutes>  (legacy)
     *   - Upload ACKs: CHUNK_ACK:<n>, UPLOAD_OK, UPLOAD_ERR
     */
    private fun handleIncomingMessage(raw: String) {
        // Upload ACKs
        if (raw.startsWith(SyncPayload.ACK_CHUNK) ||
            raw == SyncPayload.ACK_UPLOAD_OK ||
            raw == SyncPayload.ACK_UPLOAD_ERR) {
            _bleAckFlow.tryEmit(raw)
            return
        }

        // Legacy plain-text snooze (e.g. from old ESP firmware)
        if (raw.startsWith(SyncPayload.CMD_SNOOZE_PREFIX, ignoreCase = true)) {
            val minutes = raw.substring(SyncPayload.CMD_SNOOZE_PREFIX.length).toIntOrNull() ?: 10
            handleRemoteSnooze(null, null, minutes)
            return
        }

        // JSON actions
        val json = try {
            org.json.JSONObject(raw)
        } catch (_: Exception) { return }

        val action   = json.optString("action") ?: return
        val alarmId  = json.optString("alarmId").takeIf { it.isNotEmpty() }
        val ringtone = RingtoneType.fromWire(json.optString("ringtone"))

        when (action) {
            SyncPayload.ACTION_STOP_ALARM -> {
                // ESP stopped its alarm; stop phone alarm if it's the same (alarmId, ringtone)
                handleRemoteStop(alarmId, ringtone)
            }
            SyncPayload.ACTION_SNOOZE_ALARM -> {
                val minutes = json.optInt("minutes", 10)
                handleRemoteSnooze(alarmId, ringtone, minutes)
            }
            SyncPayload.ACTION_DISMISS_NEXT -> {
                // ESP toggled a dismiss; sync it to the phone's repository
                val dismissed = json.optBoolean("dismissed", true)
                handleRemoteDismiss(alarmId, ringtone, dismissed)
            }
        }
    }

    /**
     * The ESP stopped its alarm. Mirror the stop on the phone if the same
     * (alarmId, ringtone) pair is currently ringing — or if we have no ID
     * info (legacy), stop unconditionally.
     */
    private fun handleRemoteStop(alarmId: String?, ringtone: RingtoneType?) {
        if (!isPhoneAlarmRinging.value) return
        val matchesId      = alarmId == null || alarmId == AlarmRingtoneService.activeAlarmId
        val matchesSlot    = ringtone == null || ringtone == AlarmRingtoneService.activeRingtoneType
        if (matchesId && matchesSlot) {
            AlarmRingtoneService.stopAlarm(getApplication())
            _messages.update { it + DeviceMessage(
                text = "[ESP] Remote stop received — phone alarm silenced.",
                direction = MessageDirection.RX
            )}
        }
    }

    /**
     * The ESP snoozed its alarm. Mirror on phone only when the same
     * (alarmId, ringtone) is actively ringing.
     */
    private fun handleRemoteSnooze(alarmId: String?, ringtone: RingtoneType?, minutes: Int) {
        if (!isPhoneAlarmRinging.value) return
        val matchesId   = alarmId == null || alarmId == AlarmRingtoneService.activeAlarmId
        val matchesSlot = ringtone == null || ringtone == AlarmRingtoneService.activeRingtoneType
        if (matchesId && matchesSlot) {
            AlarmRingtoneService.snoozeAlarm(getApplication(), minutes)
            _messages.update { it + DeviceMessage(
                text = "[ESP] Remote snooze received: +$minutes min.",
                direction = MessageDirection.RX
            )}
        }
    }

    /**
     * The ESP toggled a dismiss-for-next for a specific alarm slot.
     * Update the repository so the phone honours the skip too.
     */
    private fun handleRemoteDismiss(alarmId: String?, ringtone: RingtoneType?, dismissed: Boolean) {
        if (alarmId == null || ringtone == null) return
        alarmRepository.setDismissed(alarmId, ringtone, dismissed)
        loadAlarms()
        _messages.update { it + DeviceMessage(
            text = "[ESP] Dismiss sync: alarm $alarmId [${ringtone.wire}] dismissed=$dismissed",
            direction = MessageDirection.RX
        )}
    }

    // ------------------------------------------------------------------
    // Alarm management
    // ------------------------------------------------------------------

    fun loadAlarms() { _alarms.value = alarmRepository.getAlarms() }

    fun addOrUpdateAlarm(
        id: String? = null,
        title: String,
        lockPhone: RingtoneSlot,
        bedtime: RingtoneSlot,
        wakeup: RingtoneSlot,
        recurrence: Recurrence,
        snoozeDurations: List<Int> = AlarmItem.DEFAULT_SNOOZE_DURATIONS,
        ringtoneFile: String? = null
    ) {
        val alarm = AlarmItem(
            id              = id ?: java.util.UUID.randomUUID().toString(),
            title           = title,
            isEnabled       = true,
            recurrence      = recurrence,
            lockPhone       = lockPhone,
            bedtime         = bedtime,
            wakeup          = wakeup,
            snoozeDurations = snoozeDurations,
            ringtoneFile    = ringtoneFile
        ).withRequiredSoundOutputs()
        alarmRepository.addOrUpdateAlarm(alarm)
        alarmScheduler.scheduleAlarm(alarm)
        loadAlarms()
        pushSyncToDevice()
    }

    fun deleteAlarm(alarm: AlarmItem) {
        alarmScheduler.cancelAlarm(alarm)
        alarmRepository.deleteAlarm(alarm.id)
        loadAlarms()
        pushSyncToDevice()
    }

    fun toggleAlarm(alarm: AlarmItem, enabled: Boolean) {
        alarmRepository.toggleAlarm(alarm.id, enabled)
        alarmScheduler.scheduleAlarm(alarm.copy(isEnabled = enabled,
            dismissedRingtones = emptySet(), isDismissedForNext = false))
        loadAlarms()
        pushSyncToDevice()
    }

    /**
     * Toggles the dismiss-for-next flag for [ringtone] on [alarm].
     * Pushes the change to the ESP so it skips the same slot.
     */
    fun toggleDismissNext(alarm: AlarmItem, ringtone: RingtoneType) {
        val newDismiss = !alarm.isDismissed(ringtone)
        val updated    = alarmRepository.setDismissed(alarm.id, ringtone, newDismiss) ?: return
        loadAlarms()

        // Notify ESP so it also skips the slot
        viewModelScope.launch {
            activeCommunicator.sendMessage(
                SyncPayload.buildDismissNext(alarm.id, ringtone, newDismiss)
            )
        }

        val slotLabel = when (ringtone) {
            RingtoneType.LOCK_PHONE -> "bedtime start"
            RingtoneType.BEDTIME -> "bedtime"
            RingtoneType.WAKEUP -> "wake-up"
        }
        _statusAlert.value = if (newDismiss)
            "${alarm.title}: next $slotLabel skipped."
        else
            "${alarm.title}: $slotLabel restored."
    }

    // ------------------------------------------------------------------
    // Sync
    // ------------------------------------------------------------------

    fun pushSyncToDevice() {
        if (connectionState.value !is ConnectionState.Connected) {
            _hasPendingSync.value = true
            return
        }
        viewModelScope.launch {
            val result = activeCommunicator.sendMessage(SyncPayload.buildPushSyncJson(_alarms.value))
            if (result.isSuccess) {
                _hasPendingSync.value = false
                _messages.update { it + DeviceMessage(
                    text = "[Sync] ${_alarms.value.size} alarm(s) pushed to device.",
                    direction = MessageDirection.TX
                )}
            } else {
                _hasPendingSync.value = true
            }
        }
    }

    fun setAutoSyncHours(hours: Int) {
        alarmRepository.setAutoSyncHours(hours)
        _autoSyncHours.value = hours
    }

    // ------------------------------------------------------------------
    // Phone alarm control
    // ------------------------------------------------------------------

    fun stopPhoneAlarm() {
        val id = AlarmRingtoneService.activeAlarmId
        val slot = AlarmRingtoneService.activeRingtoneType
        AlarmRingtoneService.stopAlarm(getApplication())
        sendStopToDevice(id, slot)
    }

    fun snoozePhoneAlarm(minutes: Int = 10) {
        val id = AlarmRingtoneService.activeAlarmId
        val slot = AlarmRingtoneService.activeRingtoneType
        AlarmRingtoneService.snoozeAlarm(getApplication(), minutes)
        sendSnoozeToDevice(id, slot, minutes)
        _statusAlert.value = "Alarm snoozed for $minutes minutes."
    }

    fun currentSnoozeDurations(): List<Int> {
        val id = AlarmRingtoneService.activeAlarmId ?: return AlarmItem.DEFAULT_SNOOZE_DURATIONS
        return _alarms.value.find { it.id == id }?.snoozeDurations ?: AlarmItem.DEFAULT_SNOOZE_DURATIONS
    }

    // ------------------------------------------------------------------
    // Remote device control
    // ------------------------------------------------------------------

    /**
     * Sends a targeted STOP_ALARM to the ESP for the given alarm and ringtone.
     * Mirrors the stop locally only when the same slot is ringing on the phone.
     */
    fun stopAlarm(alarmId: String, ringtone: RingtoneType) {
        // Stop phone side
        val phoneShouldStop = AlarmRingtoneService.activeAlarmId == alarmId &&
                              AlarmRingtoneService.activeRingtoneType == ringtone
        if (phoneShouldStop) AlarmRingtoneService.stopAlarm(getApplication())

        // Stop ESP side
        sendStopToDevice(alarmId, ringtone)
    }

    /**
     * Sends a targeted SNOOZE_ALARM to the ESP. Mirrors locally when applicable.
     */
    fun snoozeAlarm(alarmId: String, ringtone: RingtoneType, minutes: Int) {
        val phoneShouldSnooze = AlarmRingtoneService.activeAlarmId == alarmId &&
                                AlarmRingtoneService.activeRingtoneType == ringtone
        if (phoneShouldSnooze) AlarmRingtoneService.snoozeAlarm(getApplication(), minutes)

        sendSnoozeToDevice(alarmId, ringtone, minutes)
    }

    private fun sendStopToDevice(alarmId: String?, ringtone: RingtoneType?) {
        val payload = if (alarmId != null && ringtone != null) {
            SyncPayload.buildStopAlarm(alarmId, ringtone)
        } else {
            SyncPayload.buildStopAlarm()
        }
        viewModelScope.launch {
            val result = activeCommunicator.sendMessage(payload)
            if (result.isSuccess) {
                _messages.update { it + DeviceMessage(
                    text = if (alarmId != null && ringtone != null)
                        "[CMD] Stop [${ringtone.wire}] for $alarmId sent."
                    else "[CMD] Stop any active device alarm sent.",
                    direction = MessageDirection.TX
                )}
            } else {
                _statusAlert.value = "Failed to send stop: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private fun sendSnoozeToDevice(alarmId: String?, ringtone: RingtoneType?, minutes: Int) {
        val payload = if (alarmId != null && ringtone != null) {
            SyncPayload.buildSnoozeAlarm(alarmId, ringtone, minutes)
        } else {
            SyncPayload.buildSnoozeAlarm(minutes)
        }
        viewModelScope.launch {
            val result = activeCommunicator.sendMessage(payload)
            if (result.isFailure) {
                _statusAlert.value = "Failed to send snooze: ${result.exceptionOrNull()?.message}"
            } else {
                _messages.update { it + DeviceMessage(
                    text = "[CMD] Snooze ${minutes}min sent to device.",
                    direction = MessageDirection.TX
                )}
            }
        }
    }

    // Convenience overloads that act on whatever is currently ringing
    fun stopCurrentAlarm() {
        val id = AlarmRingtoneService.activeAlarmId
        val slot = AlarmRingtoneService.activeRingtoneType
        if (id != null && slot != null) {
            stopAlarm(id, slot)
        } else {
            if (isPhoneAlarmRinging.value) AlarmRingtoneService.stopAlarm(getApplication())
            sendStopToDevice(null, null)
        }
    }

    fun snoozeCurrentAlarm(minutes: Int) {
        val id = AlarmRingtoneService.activeAlarmId
        val slot = AlarmRingtoneService.activeRingtoneType
        if (id != null && slot != null) {
            snoozeAlarm(id, slot, minutes)
        } else {
            if (isPhoneAlarmRinging.value) AlarmRingtoneService.snoozeAlarm(getApplication(), minutes)
            sendSnoozeToDevice(null, null, minutes)
        }
    }

    /**
     * Pushes an immediate ring command to the ESP for [alarm]'s [ringtone] slot.
     * The device will loop until it receives a STOP_ALARM with the matching IDs.
     */
    fun pushRingtoneToDevice(alarm: AlarmItem, ringtone: RingtoneType) {
        if (connectionState.value !is ConnectionState.Connected) {
            _statusAlert.value = "Not connected, cannot push ringtone."
            return
        }
        viewModelScope.launch {
            activeCommunicator.sendMessage(SyncPayload.buildPushRingtoneJson(alarm.id, ringtone))
            _messages.update { it + DeviceMessage(
                text = "[CMD] Push ringtone [${ringtone.wire}] for '${alarm.title}' sent.",
                direction = MessageDirection.TX
            )}
        }
    }

    // ------------------------------------------------------------------
    // NFC
    // ------------------------------------------------------------------

    fun onNfcTagDetected(tagPayload: String? = null) {
        when (val result = nfcHandler.handleNfcTagScanned(tagPayload)) {
            is NfcActionHandler.NfcResult.StoppedRinging -> {
                _nfcToastMessages.trySend(result.message)
                // Mirror on ESP
                viewModelScope.launch {
                    activeCommunicator.sendMessage(
                        SyncPayload.buildStopAlarm(result.alarmId, result.ringtone)
                    )
                }
            }
            is NfcActionHandler.NfcResult.DismissedNext -> {
                _nfcToastMessages.trySend(
                    "NFC: '${result.alarmTitle}' [${result.ringtone.wire}] at ${result.time} " +
                    if (result.dismissed) "skipped." else "re-enabled."
                )
                loadAlarms()
                // Notify ESP
                viewModelScope.launch {
                    activeCommunicator.sendMessage(
                        SyncPayload.buildDismissNext(result.alarmId, result.ringtone, result.dismissed)
                    )
                }
            }
            is NfcActionHandler.NfcResult.NoAction -> {
                _nfcToastMessages.trySend(result.message)
            }
            is NfcActionHandler.NfcResult.UnknownTag -> {
                _nfcToastMessages.trySend("Unrecognized NFC tag: '${result.payload}'")
            }
        }
    }

    // ------------------------------------------------------------------
    // Ringtone upload
    // ------------------------------------------------------------------

    fun uploadRingtoneToDevice(wavUri: Uri, filename: String) {
        if (connectionState.value !is ConnectionState.Connected) {
            _uploadState.value = UploadState.Error("Not connected, cannot upload.")
            return
        }
        viewModelScope.launch {
            _uploadState.value = UploadState.InProgress(filename, 0f)

            val bytes: ByteArray = try {
                getApplication<Application>().contentResolver
                    .openInputStream(wavUri)?.use(InputStream::readBytes)
                    ?: run { _uploadState.value = UploadState.Error("Cannot open file."); return@launch }
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error("Read error: ${e.message}"); return@launch
            }

            val crc         = CRC32().apply { update(bytes) }.value
            val totalChunks = (bytes.size + SyncPayload.CHUNK_SIZE_BYTES - 1) / SyncPayload.CHUNK_SIZE_BYTES

            activeCommunicator.sendMessage(SyncPayload.buildUploadBegin(filename, bytes.size))
            delay(200)

            for (i in 0 until totalChunks) {
                val from  = i * SyncPayload.CHUNK_SIZE_BYTES
                val slice = bytes.copyOfRange(from, minOf(from + SyncPayload.CHUNK_SIZE_BYTES, bytes.size))
                val b64   = Base64.encodeToString(slice, Base64.NO_WRAP)

                var acked   = false
                var retries = 0
                while (!acked && retries < 3) {
                    activeCommunicator.sendMessage(SyncPayload.buildUploadChunk(i, b64))
                    val ack = withTimeoutOrNull(5_000) {
                        var r: String? = null
                        _bleAckFlow.collect { raw ->
                            if (raw == "${SyncPayload.ACK_CHUNK}$i") { r = raw; return@collect }
                        }
                        r
                    }
                    if (ack != null) acked = true else retries++
                }
                if (!acked) {
                    _uploadState.value = UploadState.Error("Chunk $i not acknowledged after 3 retries.")
                    return@launch
                }
                _uploadState.value = UploadState.InProgress(filename, (i + 1f) / totalChunks)
            }

            activeCommunicator.sendMessage(SyncPayload.buildUploadEnd(filename, crc))
            val finalAck = withTimeoutOrNull(8_000) {
                var r: String? = null
                _bleAckFlow.collect { raw ->
                    if (raw == SyncPayload.ACK_UPLOAD_OK || raw == SyncPayload.ACK_UPLOAD_ERR) {
                        r = raw; return@collect
                    }
                }
                r
            }

            if (finalAck == SyncPayload.ACK_UPLOAD_OK) {
                alarmRepository.addUploadedRingtone(filename)
                _uploadedRingtones.value = alarmRepository.getUploadedRingtones()
                _uploadState.value = UploadState.Success(filename)
                _messages.update { it + DeviceMessage(
                    text = "[Upload] '$filename' done (${bytes.size} bytes).",
                    direction = MessageDirection.TX
                )}
            } else {
                _uploadState.value = UploadState.Error("Device reported upload error.")
            }
        }
    }

    fun deleteRingtoneFromDevice(filename: String) {
        viewModelScope.launch {
            activeCommunicator.sendMessage(SyncPayload.buildDeleteRingtone(filename))
            alarmRepository.removeUploadedRingtone(filename)
            _uploadedRingtones.value = alarmRepository.getUploadedRingtones()
        }
    }

    // ------------------------------------------------------------------
    // NFC tag writing
    // ------------------------------------------------------------------

    fun writeNfcTag(tag: android.nfc.Tag, payload: String) {
        viewModelScope.launch {
            val result = com.example.alarmcontroller.alarm.nfc.NfcWriteManager.write(tag, payload)
            _nfcToastMessages.trySend(if (result.isSuccess)
                "Tag written: '$payload'."
            else "Write failed: ${result.exceptionOrNull()?.message}")
        }
    }

    // ------------------------------------------------------------------
    // BLE device management
    // ------------------------------------------------------------------

    fun startScan()    = bleManager.startScan()
    fun stopScan()     = bleManager.stopScan()
    fun connectDevice(address: String) = viewModelScope.launch { bleManager.connect(address) }
    fun disconnect()   { alarmRepository.clearLastBleMac(); bleManager.disconnect() }
    fun sendMessage(text: String) = viewModelScope.launch { activeCommunicator.sendMessage(text) }
    fun clearMessages() { _messages.value = emptyList() }
    fun clearAlert()    { _statusAlert.value = null }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
