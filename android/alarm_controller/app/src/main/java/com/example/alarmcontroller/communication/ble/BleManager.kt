package com.example.alarmcontroller.communication.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.alarmcontroller.communication.ConnectionState
import com.example.alarmcontroller.communication.DeviceCommunicator
import com.example.alarmcontroller.communication.DeviceMessage
import com.example.alarmcontroller.communication.MessageDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) : DeviceCommunicator {

    companion object {
        private const val TAG = "BleManager"

        // Standard Client Characteristic Configuration Descriptor (CCCD)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Nordic UART Service UUIDs (widely used on ESP32 / Arduino / nRF)
        val NORDIC_UART_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NORDIC_TX_CHAR: UUID      = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Peripheral → App (Notify)
        val NORDIC_RX_CHAR: UUID      = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // App → Peripheral (Write)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    // MAC address of the last successfully connected device; drives autoConnect reconnection.
    private var lastConnectedMac: String? = null
    // Set to true when the user explicitly calls disconnect() to suppress auto-reconnect.
    private var userDisconnected = false

    private var reconnectRetries = 0

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<DeviceMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<DeviceMessage> = _incomingMessages.asSharedFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceItem>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // ------------------------------------------------------------------
    // BLE scan
    // ------------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device  = result.device
            val address = device.address ?: return
            val name    = try { device.name ?: "Unknown device" } catch (_: SecurityException) { "Unknown device" }

            _scannedDevices.update { list ->
                val idx = list.indexOfFirst { it.address == address }
                val item = BleDeviceItem(name = name, address = address, rssi = result.rssi)
                if (idx >= 0) list.toMutableList().apply { set(idx, item) } else list + item
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with code $errorCode")
            _isScanning.value = false
        }
    }

    fun startScan(timeoutMs: Long = 15_000L) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothEnabled) {
            Log.w(TAG, "Bluetooth unavailable or scanner is null")
            return
        }
        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            scope.launch { delay(timeoutMs); stopScan() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
            _isScanning.value = false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        try { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        catch (e: Exception) { Log.e(TAG, "Error stopping scan", e) }
        finally { _isScanning.value = false }
    }

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    override suspend fun connect(target: String): Result<Unit> {
        stopScan()
        userDisconnected = false
        reconnectRetries = 0

        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth not available"))

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(target)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid MAC address: $target", e))
        }

        val deviceName = try { device.name ?: target } catch (_: SecurityException) { target }
        _connectionState.value = ConnectionState.Connecting(deviceName)

        return try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null

            // autoConnect=true: the OS reconnects in the background whenever the device
            // is in range, even when the app is not in the foreground, similar to how
            // Bluetooth headphones reconnect automatically.
            // autoConnect=false is used for the first explicit connection to avoid long
            // timeout; subsequent reconnects use autoConnect=true via reconnectInBackground().
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, gattCallback)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Connection error: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    /**
     * Registers an autoConnect watch with the OS for [mac].
     * The OS will trigger the GATT callback when the device broadcasts,
     * without any active scan. This works with the app in the background.
     * Call this once a device has been paired/used successfully.
     */
    fun reconnectInBackground(mac: String) {
        if (!isBluetoothEnabled) return
        val adapter = bluetoothAdapter ?: return
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(mac)
        } catch (e: Exception) {
            Log.w(TAG, "reconnectInBackground: invalid MAC $mac", e)
            return
        }
        // Close any previous GATT instance before starting an autoConnect session
        try { bluetoothGatt?.close() } catch (_: Exception) {}
        bluetoothGatt = null
        userDisconnected = false

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, true, gattCallback)
            }
            Log.d(TAG, "autoConnect watch registered for $mac")
        } catch (e: Exception) {
            Log.e(TAG, "reconnectInBackground failed", e)
        }
    }

    /**
     * Explicitly disconnects and suppresses any auto-reconnect attempt.
     * Call this when the user deliberately wants to disconnect.
     */
    override fun disconnect() {
        userDisconnected = true
        lastConnectedMac = null
        reconnectRetries = 0
        closeGatt()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Closes the GATT resources without clearing [lastConnectedMac],
     * so auto-reconnect can still fire afterward.
     */
    private fun closeGatt() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT", e)
        } finally {
            bluetoothGatt          = null
            writeCharacteristic    = null
            notifyCharacteristic   = null
        }
    }

    // ------------------------------------------------------------------
    // Send
    // ------------------------------------------------------------------

    override suspend fun sendMessage(message: String): Result<Unit> {
        val gatt = bluetoothGatt
            ?: return Result.failure(IllegalStateException("Not connected to any device"))
        val char = writeCharacteristic
            ?: return Result.failure(IllegalStateException("No writable characteristic found"))

        val data = message.toByteArray(Charsets.UTF_8)

        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val writeType = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(char, data, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = data
            char.writeType = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }

        return if (success) {
            _incomingMessages.tryEmit(DeviceMessage(text = message, direction = MessageDirection.TX))
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to send command"))
        }
    }

    // ------------------------------------------------------------------
    // GATT callback
    // ------------------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            val name    = try { gatt.device.name ?: address } catch (_: SecurityException) { address }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to $address: starting service discovery")
                    _connectionState.value = ConnectionState.Connecting("Discovering services…")
                    // Short delay recommended by Android BLE docs for stability
                    scope.launch {
                        delay(300)
                        val started = gatt.discoverServices()
                        if (!started) {
                            Log.e(TAG, "discoverServices() returned false")
                            _connectionState.value = ConnectionState.Error("Could not discover device services")
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from $address (status=$status)")
                    val mac = lastConnectedMac
                    closeGatt()
                    _connectionState.value = ConnectionState.Disconnected
                    // If this was an unexpected disconnection (not user-initiated),
                    // register an autoConnect watch so the OS reconnects when the
                    // device comes back into range, no active scan needed.
                    if (mac != null && !userDisconnected) {
                        reconnectInBackground(mac)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val address = gatt.device.address
            val name    = try { gatt.device.name ?: address } catch (_: SecurityException) { address }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status $status")
                _connectionState.value = ConnectionState.Error("Service discovery error ($status)")
                return
            }

            Log.d(TAG, "${gatt.services.size} services discovered on $address")
            configureCharacteristics(gatt, name, address)
            // Request a larger MTU to fit big sync JSON in one packet
            gatt.requestMtu(512)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU negotiated: $mtu (status=$status)")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleReceivedBytes(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleReceivedBytes(value)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun handleReceivedBytes(data: ByteArray) {
        val text = String(data, Charsets.UTF_8).trim()
        if (text.isNotEmpty()) {
            _incomingMessages.tryEmit(DeviceMessage(text = text, direction = MessageDirection.RX))
        }
    }

    private fun configureCharacteristics(gatt: BluetoothGatt, deviceName: String, deviceAddress: String) {
        var foundWrite:  BluetoothGattCharacteristic? = null
        var foundNotify: BluetoothGattCharacteristic? = null

        // 1. Prefer the Nordic UART service if available
        val uartService = gatt.getService(NORDIC_UART_SERVICE)
        if (uartService != null) {
            foundWrite  = uartService.getCharacteristic(NORDIC_RX_CHAR)
            foundNotify = uartService.getCharacteristic(NORDIC_TX_CHAR)
        }

        // 2. Fallback: scan all services for a writable + notifiable characteristic
        if (foundWrite == null || foundNotify == null) {
            for (service in gatt.services) {
                val isGenericMeta = service.uuid.toString().run {
                    startsWith("00001800") || startsWith("00001801")
                }
                for (char in service.characteristics) {
                    val props      = char.properties
                    val canWrite   = props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                                     props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                    val canNotify  = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                                     props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    if (!isGenericMeta) {
                        if (foundWrite  == null && canWrite)  foundWrite  = char
                        if (foundNotify == null && canNotify) foundNotify = char
                    }
                }
            }
        }

        writeCharacteristic  = foundWrite
        notifyCharacteristic = foundNotify

        Log.d(TAG, "Write characteristic : ${writeCharacteristic?.uuid}")
        Log.d(TAG, "Notify characteristic: ${notifyCharacteristic?.uuid}")

        // Enable GATT notifications
        foundNotify?.let { char ->
            gatt.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val value = if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        // Mark as connected, this triggers the auto-sync in MainViewModel
        lastConnectedMac    = deviceAddress
        reconnectRetries    = 0
        _connectionState.value = ConnectionState.Connected(deviceName, deviceAddress)

        val readyMsg = if (foundWrite != null)
            "Ready to communicate!\nTX (Write): ${foundWrite.uuid}\nRX (Notify): ${foundNotify?.uuid ?: "unavailable"}"
        else
            "Warning: no writable characteristic found on this device."
        _incomingMessages.tryEmit(DeviceMessage(text = readyMsg, direction = MessageDirection.RX))
    }
}
