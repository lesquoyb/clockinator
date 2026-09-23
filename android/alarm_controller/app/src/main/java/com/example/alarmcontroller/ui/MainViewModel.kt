package com.example.alarmcontroller.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.AlarmRepository
import com.example.alarmcontroller.alarm.AlarmRingtoneService
import com.example.alarmcontroller.alarm.AlarmScheduler
import com.example.alarmcontroller.alarm.Recurrence
import com.example.alarmcontroller.alarm.nfc.NfcActionHandler
import com.example.alarmcontroller.communication.ConnectionState
import com.example.alarmcontroller.communication.DeviceCommunicator
import com.example.alarmcontroller.communication.DeviceMessage
import com.example.alarmcontroller.communication.MessageDirection
import com.example.alarmcontroller.communication.SyncPayload
import com.example.alarmcontroller.communication.ble.BleDeviceItem
import com.example.alarmcontroller.communication.ble.BleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommunicatorType {
    BLE,
    WIFI
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)
    private val alarmRepository = AlarmRepository(application)
    private val alarmScheduler = AlarmScheduler(application)
    private val nfcHandler = NfcActionHandler(application)

    private var activeCommunicator: DeviceCommunicator = bleManager

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val scannedDevices: StateFlow<List<BleDeviceItem>> = bleManager.scannedDevices
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val isBluetoothEnabled: Boolean get() = bleManager.isBluetoothEnabled

    private val _communicatorType = MutableStateFlow(CommunicatorType.BLE)
    val communicatorType: StateFlow<CommunicatorType> = _communicatorType.asStateFlow()

    private val _messages = MutableStateFlow<List<DeviceMessage>>(emptyList())
    val messages: StateFlow<List<DeviceMessage>> = _messages.asStateFlow()

    private val _statusAlert = MutableStateFlow<String?>(null)
    val statusAlert: StateFlow<String?> = _statusAlert.asStateFlow()

    // Alarm States
    private val _alarms = MutableStateFlow<List<AlarmItem>>(emptyList())
    val alarms: StateFlow<List<AlarmItem>> = _alarms.asStateFlow()

    val isPhoneAlarmRinging: StateFlow<Boolean> = AlarmRingtoneService.isRinging
    val ringingAlarmTitle: StateFlow<String?> = AlarmRingtoneService.ringingAlarmTitle

    init {
        loadAlarms()

        // 1. Écouter les messages entrants (notamment le SNOOZE envoyé par le périphérique BLE)
        viewModelScope.launch {
            bleManager.incomingMessages.collect { message ->
                _messages.update { current -> current + message }

                if (message.direction == MessageDirection.RX) {
                    val rawText = message.text.uppercase()
                    if (rawText.contains("SNOOZE")) {
                        // Extrait optionnel de durée (ex: SNOOZE:10)
                        val minutes = Regex("SNOOZE:?(\\d+)?").find(rawText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10
                        handleBleSnoozeReceived(minutes)
                    }
                }
            }
        }

        // 2. Push Sync automatique dès que la connexion BLE est établie
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    delay(1000) // Petit délai pour laisser les descripteurs s'activer
                    pushSyncToDevice()
                }
            }
        }
    }

    fun loadAlarms() {
        _alarms.value = alarmRepository.getAlarms()
    }

    fun addOrUpdateAlarm(
        id: String? = null,
        title: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        recurrence: Recurrence
    ) {
        val alarm = AlarmItem(
            id = id ?: java.util.UUID.randomUUID().toString(),
            title = title,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            isEnabled = true,
            isDismissedForNext = false,
            recurrence = recurrence
        )
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
        val updated = alarm.copy(isEnabled = enabled, isDismissedForNext = false)
        alarmScheduler.scheduleAlarm(updated)
        loadAlarms()
        pushSyncToDevice()
    }

    /**
     * "Cette fois tu ne te déclenches pas" (Dismiss pour le prochain cycle)
     */
    fun toggleDismissNext(alarm: AlarmItem) {
        val newDismiss = !alarm.isDismissedForNext
        alarmRepository.dismissNext(alarm.id, newDismiss)
        loadAlarms()
        pushSyncToDevice()

        val msg = if (newDismiss) {
            "L'alarme '${alarm.title}' ne se déclenchera pas au prochain horaire."
        } else {
            "L'alarme '${alarm.title}' est rétablie pour le prochain horaire."
        }
        _statusAlert.value = msg
    }

    /**
     * Pousse l'heure système et la liste des alarmes vers le périphérique BLE.
     * Le téléphone est l'unique source de vérité.
     */
    fun pushSyncToDevice() {
        viewModelScope.launch {
            if (connectionState.value !is ConnectionState.Connected) return@launch

            val payload = SyncPayload.buildPushSyncJson(_alarms.value)
            val result = activeCommunicator.sendMessage(payload)
            if (result.isSuccess) {
                _messages.update { current ->
                    current + DeviceMessage(
                        text = "📤 [Push Sync] Heure + ${_alarms.value.size} alarme(s) synchronisées avec le device.",
                        direction = MessageDirection.TX
                    )
                }
            }
        }
    }

    /**
     * Arrête l'alarme en cours qui sonne sur le téléphone.
     */
    fun stopPhoneAlarm() {
        AlarmRingtoneService.stopAlarm(getApplication())
    }

    /**
     * Snooze (répétition) de l'alarme du téléphone.
     */
    fun snoozePhoneAlarm(minutes: Int = 10) {
        AlarmRingtoneService.snoozeAlarm(getApplication(), minutes)
        _statusAlert.value = "Alarme répétée dans $minutes minutes."
    }

    /**
     * Appelé lorsque le périphérique BLE envoie la commande SNOOZE.
     */
    private fun handleBleSnoozeReceived(minutes: Int) {
        if (isPhoneAlarmRinging.value) {
            snoozePhoneAlarm(minutes)
            _messages.update { current ->
                current + DeviceMessage(
                    text = "📥 [BLE SNOOZE] Alarme du téléphone mise en pause (+ $minutes min) suite au signal reçu du device distant.",
                    direction = MessageDirection.RX
                )
            }
        }
    }

    /**
     * Envoie l'ordre au périphérique distant d'arrêter sa propre alarme.
     */
    fun stopRemoteAlarm() {
        viewModelScope.launch {
            val result = activeCommunicator.sendMessage(SyncPayload.CMD_STOP_REMOTE_ALARM)
            if (result.isSuccess) {
                _messages.update { current ->
                    current + DeviceMessage(
                        text = "🛑 [Commande] Ordre d'arrêt envoyé à l'alarme distante (${SyncPayload.CMD_STOP_REMOTE_ALARM})",
                        direction = MessageDirection.TX
                    )
                }
                _statusAlert.value = "Ordre d'arrêt envoyé à l'appareil distant."
            } else {
                _statusAlert.value = "Échec d'envoi : ${result.exceptionOrNull()?.message}"
            }
        }
    }

    /**
     * Traite un tag NFC scanné (ou déclenché par un test virtuel).
     */
    fun onNfcTagDetected() {
        val result = nfcHandler.handleNfcTagScanned()
        when (result) {
            is NfcActionHandler.NfcResult.StoppedRingingAlarm -> {
                _statusAlert.value = result.message
                // Quand on scanne un tag NFC pour arrêter le téléphone, on peut aussi ordonner l'arrêt du device BLE !
                stopRemoteAlarm()
            }
            is NfcActionHandler.NfcResult.DismissedNextAlarm -> {
                _statusAlert.value = "Tag NFC : La prochaine alarme '${result.alarmTitle}' (${result.time}) a été ignorée pour ce cycle."
                loadAlarms()
                pushSyncToDevice()
            }
            is NfcActionHandler.NfcResult.NoAction -> {
                _statusAlert.value = result.message
                loadAlarms()
                pushSyncToDevice()
            }
        }
    }

    // BLE scanning & management
    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connectDevice(address: String) = viewModelScope.launch { bleManager.connect(address) }
    fun disconnect() = bleManager.disconnect()
    fun sendMessage(text: String) = viewModelScope.launch { activeCommunicator.sendMessage(text) }
    fun clearMessages() { _messages.value = emptyList() }
    fun clearAlert() { _statusAlert.value = null }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
