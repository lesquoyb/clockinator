package com.example.alarmcontroller.communication.wifi

import com.example.alarmcontroller.communication.ConnectionState
import com.example.alarmcontroller.communication.DeviceCommunicator
import com.example.alarmcontroller.communication.DeviceMessage
import com.example.alarmcontroller.communication.MessageDirection
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Inet4Address
import java.net.Socket
import java.nio.charset.StandardCharsets

class WifiCommunicator(context: Context) : DeviceCommunicator {
    @Volatile private var enabled = true
    fun setEnabled(value: Boolean) { enabled = value; if (!value) disconnect() }

    companion object {
        private const val PORT = 4210
        private const val CONNECT_TIMEOUT_MS = 15_000
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<DeviceMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<DeviceMessage> = _incomingMessages.asSharedFlow()

    fun reportConnecting(target: String) {
        if (!enabled) return
        _connectionState.value = ConnectionState.Connecting(target)
    }

    fun reportError(message: String) {
        if (!enabled) return
        disconnect()
        _connectionState.value = ConnectionState.Error(message)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val writeMutex = Mutex()
    @Volatile private var socket: Socket? = null
    private var readerJob: Job? = null
    override suspend fun connect(target: String): Result<Unit> = connect(target, null)


    suspend fun connect(target: String, preferredNetwork: Network?): Result<Unit> = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext Result.failure(IllegalStateException("Wi-Fi sync is disabled"))
        disconnect()
        _connectionState.value = ConnectionState.Connecting("$target:$PORT via Wi-Fi")
        val wifiNetwork = preferredNetwork ?: findWifiNetwork()
        if (wifiNetwork == null) {
            val message = "No active Wi-Fi network on the phone; connect it to the ESP32's local network."
            _connectionState.value = ConnectionState.Error(message)
            return@withContext Result.failure(IllegalStateException(message))
        }
        val newSocket = Socket()
        socket = newSocket // disconnect() can also cancel an in-flight connection.
        try {
            wifiNetwork.bindSocket(newSocket)
            newSocket.connect(InetSocketAddress(target, PORT), CONNECT_TIMEOUT_MS)
            newSocket.tcpNoDelay = true
            if (!enabled) { newSocket.close(); return@withContext Result.failure(IllegalStateException("Wi-Fi sync is disabled")) }
            socket = newSocket
            _connectionState.value = ConnectionState.Connected("Clockinator Wi-Fi", target)
            readerJob = scope.launch { readMessages(newSocket) }
            Result.success(Unit)
        } catch (error: Exception) {
            try { newSocket.close() } catch (_: Exception) {}
            val message = "TCP connection to ESP32 at $target:$PORT via Wi-Fi failed: " +
                (error.localizedMessage ?: error.javaClass.simpleName) + ". " +
                describeWifiRoute(wifiNetwork)
            if (enabled) _connectionState.value = ConnectionState.Error(message)
            Result.failure(error)
        }
    }

    private fun findWifiNetwork(): Network? = connectivityManager.allNetworks.firstOrNull { network ->
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    @Suppress("DEPRECATION")
    fun currentWifiSsid(): String? = try {
        wifiManager.connectionInfo?.ssid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
    } catch (_: SecurityException) {
        null
    }

    private fun describeWifiRoute(network: Network): String {
        val properties = connectivityManager.getLinkProperties(network)
            ?: return "Phone Wi-Fi route details are unavailable."
        val address = properties.linkAddresses.firstOrNull { it.address is Inet4Address }
        val gateway = properties.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val localAddress = address?.let { "${it.address.hostAddress}/${it.prefixLength}" }
            ?: "unknown"
        return "Phone Wi-Fi IP: $localAddress; gateway: ${gateway ?: "unknown"}."
    }

    override fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
        readerJob?.cancel()
        readerJob = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    override suspend fun sendMessage(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val activeSocket = socket
                ?: return@withLock Result.failure(IllegalStateException("Wi-Fi socket is not connected"))
            try {
                activeSocket.getOutputStream().apply {
                    write((message + "\n").toByteArray(StandardCharsets.UTF_8))
                    flush()
                }
                    _incomingMessages.tryEmit(DeviceMessage(text = message, direction = MessageDirection.TX))
                Result.success(Unit)
            } catch (error: Exception) {
                disconnect()
                Result.failure(error)
            }
        }
    }

    private fun readMessages(activeSocket: Socket) {
        try {
            BufferedReader(InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8)).use { reader ->
                while (!activeSocket.isClosed) {
                    val message = reader.readLine() ?: break
                    if (message.isNotBlank()) {
                        _incomingMessages.tryEmit(DeviceMessage(text = message, direction = MessageDirection.RX))
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            if (socket === activeSocket) {
                socket = null
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }
}
