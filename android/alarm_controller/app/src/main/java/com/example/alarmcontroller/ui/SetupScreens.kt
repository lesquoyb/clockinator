package com.example.alarmcontroller.ui
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.alarmcontroller.communication.ConnectionState
import com.example.alarmcontroller.communication.ble.BleDeviceItem
import androidx.core.content.ContextCompat

@Composable
fun SetupDeviceScreen(
    hasPermissions: Boolean,
    connectionState: ConnectionState,
    devices: List<BleDeviceItem>,
    isScanning: Boolean,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String, String, String) -> Unit,
    onContinue: () -> Unit
) {
    val isConnected = connectionState is ConnectionState.Connected
    var wifiSsid by rememberSaveable { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions && !isConnected) onStartScan()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("SETUP  ·  01 / 02", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Connect your alarm clock", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Pair your Clockinator once. It will reconnect automatically when you open the app.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        WifiCredentialsFields(
            ssid = wifiSsid,
            onSsidChange = { wifiSsid = it },
            password = wifiPassword,
            onPasswordChange = { wifiPassword = it }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        when (connectionState) {
                            is ConnectionState.Connected -> connectionState.deviceName
                            is ConnectionState.Connecting -> "Connecting…"
                            is ConnectionState.Error -> "Connection failed"
                            ConnectionState.Disconnected -> "No alarm clock connected"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        when (connectionState) {
                            is ConnectionState.Connected -> "Ready to sync alarms"
                            is ConnectionState.Connecting -> "Keep your alarm clock nearby"
                            is ConnectionState.Error -> connectionState.message
                            ConnectionState.Disconnected -> "Turn on your Clockinator to find it"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isConnected) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        if (!hasPermissions) {
            Text("Bluetooth permission is needed to find your alarm clock.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Allow Bluetooth")
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Nearby devices", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { if (isScanning) onStopScan() else onStartScan() }) {
                    Text(if (isScanning) "Stop" else "Scan again")
                }
            }
            if (isScanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
            val visibleDevices = devices.sortedByDescending { it.rssi }.take(5)
            if (visibleDevices.isEmpty()) {
                Text(if (isScanning) "Looking for your Clockinator…" else "No devices found. Keep it nearby and scan again.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp))
            } else {
                visibleDevices.forEach { device ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleSmall)
                            Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onConnect(device.address, wifiSsid.trim(), wifiPassword) }) {
                            Text("Connect")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinue, enabled = isConnected, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun WifiCredentialsFields(
    ssid: String,
    onSsidChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showTitle: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) Text("Local Wi-Fi (optional)", style = MaterialTheme.typography.titleSmall)
        CurrentWifiNetworkButton(onSsidChange)
        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChange,
            label = { Text("Network name (SSID)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (ssid.isNotBlank()) {
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Wi-Fi password (if required)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CurrentWifiNetworkButton(onSsidFound: (String) -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var isFrequencyWarning by remember { mutableStateOf(false) }
    val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val network = if (allGranted) readCurrentWifiNetwork(context) else null
        showCurrentNetwork(network, onSsidFound) { text, warning ->
            message = text
            isFrequencyWarning = warning
        }
    }

    OutlinedButton(
        onClick = {
            val missingPermissions = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isEmpty()) {
                val network = readCurrentWifiNetwork(context)
                showCurrentNetwork(network, onSsidFound) { text, warning ->
                    message = text
                    isFrequencyWarning = warning
                }
            } else {
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Wifi, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text("Use phone's current Wi-Fi")
    }
    message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFrequencyWarning) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class CurrentWifiNetwork(val ssid: String, val frequencyMhz: Int)

private fun showCurrentNetwork(
    network: CurrentWifiNetwork?,
    onSsidFound: (String) -> Unit,
    onMessage: (String, Boolean) -> Unit
) {
    if (network == null) {
        onMessage("Could not read the current Wi-Fi network. Check permissions or enter its name below.", false)
        return
    }
    onSsidFound(network.ssid)
    val is5Ghz = network.frequencyMhz >= 5_000
    onMessage(
        if (is5Ghz) "Phone is using 5 GHz. ESP32 band support depends on its model; many models support only 2.4 GHz."
        else "Network name filled in. Android keeps the password private.",
        is5Ghz
    )
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun readCurrentWifiNetwork(context: Context): CurrentWifiNetwork? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return null
    val wifiInfo = wifiManager.connectionInfo ?: return null
    val ssid = wifiInfo.ssid?.removeSurrounding("\"")?.trim()
        ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
        ?: return null
    return CurrentWifiNetwork(ssid, wifiInfo.frequency)
}

@Composable
fun SetupAlarmScreen(
    onCreateAlarm: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("SETUP  ·  02 / 02", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Set up your first routine", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Set Focus, optional Bedtime, Wake-up and the days it repeats.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Your routine stays in sync", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Your phone and Clockinator follow the same schedule.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCreateAlarm, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Alarm, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Create first routine")
        }
    }
}
