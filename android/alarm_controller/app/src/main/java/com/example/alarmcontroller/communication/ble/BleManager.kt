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

        // Nordic UART Service (commonly used in Arduino, ESP32, nRF chips)
        val NORDIC_UART_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val NORDIC_TX_CHAR: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Peripheral -> App (Notify)
        val NORDIC_RX_CHAR: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // App -> Peripheral (Write)
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

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device
            val address = device.address ?: return
            val name = try {
                device.name ?: "Appareil inconnu"
            } catch (e: SecurityException) {
                "Appareil inconnu"
            }

            _scannedDevices.update { currentList ->
                val existingIndex = currentList.indexOfFirst { it.address == address }
                val updatedItem = BleDeviceItem(name = name, address = address, rssi = result.rssi)
                if (existingIndex >= 0) {
                    currentList.toMutableList().apply { set(existingIndex, updatedItem) }
                } else {
                    currentList + updatedItem
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with code $errorCode")
            _isScanning.value = false
        }
    }

    fun startScan(timeoutMs: Long = 15000L) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothEnabled) {
            Log.w(TAG, "Bluetooth not available or scanner is null")
            return
        }

        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            scope.launch {
                delay(timeoutMs)
                stopScan()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
            _isScanning.value = false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        } finally {
            _isScanning.value = false
        }
    }

    override suspend fun connect(target: String): Result<Unit> {
        stopScan()

        val adapter = bluetoothAdapter ?: return Result.failure(IllegalStateException("Bluetooth non disponible"))
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(target)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Adresse MAC invalide: $target", e))
        }

        val deviceName = try {
            device.name ?: target
        } catch (e: SecurityException) {
            target
        }

        _connectionState.value = ConnectionState.Connecting(deviceName)

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null

            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
            return Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Erreur de connexion: ${e.localizedMessage}")
            return Result.failure(e)
        }
    }

    override fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting GATT", e)
        } finally {
            bluetoothGatt = null
            writeCharacteristic = null
            notifyCharacteristic = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun sendMessage(message: String): Result<Unit> {
        val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("Non connecté à un appareil"))
        val char = writeCharacteristic ?: return Result.failure(IllegalStateException("Aucune caractéristique d'écriture trouvée"))

        val data = message.toByteArray(Charsets.UTF_8)

        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val writeType = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            gatt.writeCharacteristic(char, data, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = data
            if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }

        return if (success) {
            _incomingMessages.tryEmit(
                DeviceMessage(
                    text = message,
                    direction = MessageDirection.TX
                )
            )
            Result.success(Unit)
        } else {
            Result.failure(Exception("Échec de l'envoi de la commande"))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            val name = try {
                device.name ?: device.address
            } catch (e: SecurityException) {
                device.address
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connecté à ${device.address}. Lancement découverte des services...")
                _connectionState.value = ConnectionState.Connecting("Découverte des services...")
                
                // Discuter des services après un court délai (recommandé pour stabilité BLE Android)
                scope.launch {
                    delay(300)
                    val started = gatt.discoverServices()
                    if (!started) {
                        Log.e(TAG, "Échec du démarrage de discoverServices")
                        _connectionState.value = ConnectionState.Error("Impossible d'explorer les services de l'appareil")
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Déconnecté de ${device.address}")
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val device = gatt.device
            val name = try {
                device.name ?: device.address
            } catch (e: SecurityException) {
                device.address
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Échec découverte services : $status")
                _connectionState.value = ConnectionState.Error("Erreur découverte des services ($status)")
                return
            }

            Log.d(TAG, "Services découverts (${gatt.services.size} services)")
            configureCharacteristics(gatt, name, device.address)
            
            // Demande d'un MTU plus grand après la découverte
            gatt.requestMtu(512)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU négocié : $mtu (status: $status)")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: return
            handleReceivedBytes(data)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleReceivedBytes(value)
        }
    }

    private fun handleReceivedBytes(data: ByteArray) {
        val text = String(data, Charsets.UTF_8).trim()
        if (text.isNotEmpty()) {
            _incomingMessages.tryEmit(
                DeviceMessage(
                    text = text,
                    direction = MessageDirection.RX
                )
            )
        }
    }

    private fun configureCharacteristics(gatt: BluetoothGatt, deviceName: String, deviceAddress: String) {
        var foundWrite: BluetoothGattCharacteristic? = null
        var foundNotify: BluetoothGattCharacteristic? = null

        val discoveredSummary = StringBuilder()

        // 1. Try to find Nordic UART Service first
        val uartService = gatt.getService(NORDIC_UART_SERVICE)
        if (uartService != null) {
            foundWrite = uartService.getCharacteristic(NORDIC_RX_CHAR)
            foundNotify = uartService.getCharacteristic(NORDIC_TX_CHAR)
        }

        // 2. Fallback: Search all discovered services for writable and notifiable characteristics
        if (foundWrite == null || foundNotify == null) {
            for (service in gatt.services) {
                // Ignore standard generic services for default TX/RX fallback if possible
                val isStandardBleMeta = service.uuid.toString().startsWith("00001800") ||
                        service.uuid.toString().startsWith("00001801")

                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                            (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                    val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
                            (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)

                    val shortUuid = characteristic.uuid.toString().take(8)
                    discoveredSummary.append("\n• Char $shortUuid: [${if (canWrite) "WRITE " else ""}${if (canNotify) "NOTIFY " else ""}]")

                    if (!isStandardBleMeta) {
                        if (foundWrite == null && canWrite) {
                            foundWrite = characteristic
                        }
                        if (foundNotify == null && canNotify) {
                            foundNotify = characteristic
                        }
                    }
                }
            }
        }

        writeCharacteristic = foundWrite
        notifyCharacteristic = foundNotify

        Log.d(TAG, "Caractéristique Write : ${writeCharacteristic?.uuid}")
        Log.d(TAG, "Caractéristique Notify : ${notifyCharacteristic?.uuid}")

        // Enable notifications on the RX/Notify characteristic
        foundNotify?.let { char ->
            val enabled = gatt.setCharacteristicNotification(char, true)
            Log.d(TAG, "setCharacteristicNotification: $enabled pour ${char.uuid}")

            val descriptor = char.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val value = if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }

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

        // Set connected state once services are configured
        _connectionState.value = ConnectionState.Connected(deviceName, deviceAddress)

        // Inform user via console messages
        if (foundWrite != null) {
            _incomingMessages.tryEmit(
                DeviceMessage(
                    text = "Prêt à échanger !\nTX (Écriture) : ${foundWrite.uuid}\nRX (Notification) : ${foundNotify?.uuid ?: "Non disponible"}",
                    direction = MessageDirection.RX
                )
            )
        } else {
            _incomingMessages.tryEmit(
                DeviceMessage(
                    text = "Attention : Aucune caractéristique avec permission d'écriture trouvée.$discoveredSummary",
                    direction = MessageDirection.RX
                )
            )
        }
    }
}
