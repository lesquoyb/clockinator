package com.example.alarmcontroller.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.communication.ConnectionState
import com.example.alarmcontroller.communication.DeviceMessage
import com.example.alarmcontroller.communication.MessageDirection
import com.example.alarmcontroller.communication.ble.BleDeviceItem
import com.example.alarmcontroller.ui.theme.RxBadgeColor
import com.example.alarmcontroller.ui.theme.StatusConnected
import com.example.alarmcontroller.ui.theme.StatusConnecting
import com.example.alarmcontroller.ui.theme.StatusDisconnected
import com.example.alarmcontroller.ui.theme.TxBadgeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val statusAlert by viewModel.statusAlert.collectAsState()
    val alarms by viewModel.alarms.collectAsState()

    val isPhoneAlarmRinging by viewModel.isPhoneAlarmRinging.collectAsState()
    val ringingTitle by viewModel.ringingAlarmTitle.collectAsState()

    val isConnected = connectionState is ConnectionState.Connected
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Alarm Controller",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isConnected) "Connecté au périphérique" else "Bluetooth déconnecté",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isConnected) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { viewModel.pushSyncToDevice() }, enabled = isConnected) {
                            Icon(Icons.Default.CloudSync, contentDescription = "Push Sync")
                        }
                    } else {
                        IconButton(onClick = { viewModel.clearMessages() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Effacer messages")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Bannière d'urgence si une alarme sonne actuellement sur le téléphone
            if (isPhoneAlarmRinging) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⏰ L'ALARME SONNE : ${ringingTitle ?: "Alarme"}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.stopPhoneAlarm() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ARRÊTER")
                            }
                            OutlinedButton(
                                onClick = { viewModel.snoozePhoneAlarm(10) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Répéter (+10 min)")
                            }
                        }
                        Text(
                            text = "💡 Vous pouvez aussi scanner un tag NFC pour couper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // Onglets de navigation
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Alarmes (${alarms.size})") },
                    icon = { Icon(Icons.Default.Alarm, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Bluetooth & Synchro") },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                )
            }

            // Contenu des onglets
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (selectedTab == 0) {
                    AlarmsTabContent(
                        alarms = alarms,
                        isConnected = isConnected,
                        onAddAlarm = {
                            editingAlarm = null
                            showAlarmDialog = true
                        },
                        onEditAlarm = {
                            editingAlarm = it
                            showAlarmDialog = true
                        },
                        onToggleAlarm = { alarm, enabled -> viewModel.toggleAlarm(alarm, enabled) },
                        onDismissNext = { alarm -> viewModel.toggleDismissNext(alarm) },
                        onDeleteAlarm = { alarm -> viewModel.deleteAlarm(alarm) },
                        onStopRemoteAlarm = { viewModel.stopRemoteAlarm() },
                        onSimulateNfc = { viewModel.onNfcTagDetected() }
                    )
                } else {
                    BluetoothTabContent(
                        viewModel = viewModel,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = onRequestPermissions,
                        connectionState = connectionState,
                        scannedDevices = scannedDevices,
                        isScanning = isScanning,
                        messages = messages,
                        isConnected = isConnected
                    )
                }
            }
        }

        // Boîte de dialogue pour créer ou modifier une alarme
        if (showAlarmDialog) {
            AlarmDialog(
                initialAlarm = editingAlarm,
                onDismiss = { showAlarmDialog = false },
                onSave = { title, sH, sM, eH, eM, recurrence ->
                    viewModel.addOrUpdateAlarm(
                        id = editingAlarm?.id,
                        title = title,
                        startHour = sH,
                        startMinute = sM,
                        endHour = eH,
                        endMinute = eM,
                        recurrence = recurrence
                    )
                    showAlarmDialog = false
                }
            )
        }

        // Boîte d'alerte / feedback
        if (statusAlert != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearAlert() },
                title = { Text("Information") },
                text = { Text(statusAlert ?: "") },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearAlert() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun AlarmsTabContent(
    alarms: List<AlarmItem>,
    isConnected: Boolean,
    onAddAlarm: () -> Unit,
    onEditAlarm: (AlarmItem) -> Unit,
    onToggleAlarm: (AlarmItem, Boolean) -> Unit,
    onDismissNext: (AlarmItem) -> Unit,
    onDeleteAlarm: (AlarmItem) -> Unit,
    onStopRemoteAlarm: () -> Unit,
    onSimulateNfc: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Barre d'actions rapides (Arrêt distant + Simulation NFC)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Actions rapides & Tags NFC :",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onStopRemoteAlarm,
                        enabled = isConnected,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🛑 Arrêter alarme distante", fontSize = 11.sp)
                    }
                    Button(
                        onClick = onSimulateNfc,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🏷️ Tag NFC (Scan)", fontSize = 11.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Liste des alarmes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onAddAlarm) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ajouter")
            }
        }

        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucune alarme enregistrée. Cliquez sur 'Ajouter'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmItemCard(
                        alarm = alarm,
                        onEdit = { onEditAlarm(alarm) },
                        onToggle = { enabled -> onToggleAlarm(alarm, enabled) },
                        onDismissNext = { onDismissNext(alarm) },
                        onDelete = { onDeleteAlarm(alarm) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmItemCard(
    alarm: AlarmItem,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDismissNext: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isDismissedForNext) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = alarm.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${alarm.startFormatted} ➔ ${alarm.endFormatted}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Statut & Commande Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge d'état
                if (alarm.isDismissedForNext) {
                    AssistChip(
                        onClick = onDismissNext,
                        label = { Text("Ignorée pour cette fois (Dismiss)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.AlarmOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp)) }
                    )
                } else if (alarm.isEnabled) {
                    AssistChip(
                        onClick = onDismissNext,
                        label = { Text("Passer pour cette fois", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                } else {
                    Text("Alarme désactivée", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BluetoothTabContent(
    viewModel: MainViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    connectionState: ConnectionState,
    scannedDevices: List<BleDeviceItem>,
    isScanning: Boolean,
    messages: List<DeviceMessage>,
    isConnected: Boolean
) {
    var textToSend by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Permissions Warning
        if (!hasPermissions) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Permissions Bluetooth requises",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Autoriser l'accès Bluetooth")
                    }
                }
            }
        }

        // Connection Status Banner
        ConnectionStatusCard(
            connectionState = connectionState,
            onDisconnect = { viewModel.disconnect() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Scanner BLE avec LazyColumn
        ScanSection(
            isScanning = isScanning,
            isConnected = isConnected,
            devices = scannedDevices,
            onStartScan = { viewModel.startScan() },
            onStopScan = { viewModel.stopScan() },
            onConnectDevice = { address -> viewModel.connectDevice(address) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Push Sync & Quick Commands
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.pushSyncToDevice() },
                enabled = isConnected,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Push Sync Heure/Alarmes", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Messages Console
        Text(
            text = "Console d'échanges (${messages.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun message échangé pour le moment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageItemRow(msg)
                    }
                }
            }
        }

        // Send Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textToSend,
                onValueChange = { textToSend = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message ou commande BLE...") },
                singleLine = true,
                enabled = isConnected
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    viewModel.sendMessage(textToSend)
                    textToSend = ""
                },
                enabled = isConnected && textToSend.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isConnected && textToSend.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Envoyer",
                    tint = if (isConnected && textToSend.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    connectionState: ConnectionState,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is ConnectionState.Connected -> StatusConnected.copy(alpha = 0.15f)
                is ConnectionState.Connecting -> StatusConnecting.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                is ConnectionState.Connected -> StatusConnected
                                is ConnectionState.Connecting -> StatusConnecting
                                is ConnectionState.Error -> StatusDisconnected
                                else -> Color.Gray
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    val label = when (connectionState) {
                        is ConnectionState.Connected -> "Connecté : ${connectionState.deviceName}"
                        is ConnectionState.Connecting -> "Connexion en cours..."
                        is ConnectionState.Error -> "Erreur : ${connectionState.message}"
                        ConnectionState.Disconnected -> "Déconnecté"
                    }
                    Text(
                        text = label,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (connectionState is ConnectionState.Connected) {
                        Text(
                            text = connectionState.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (connectionState is ConnectionState.Connected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDisconnected)
                ) {
                    Text("Déconnecter", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ScanSection(
    isScanning: Boolean,
    isConnected: Boolean,
    devices: List<BleDeviceItem>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(!isConnected) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recherche BLE (${devices.size})",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Row {
                    if (isScanning) {
                        Button(
                            onClick = onStopScan,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Arrêter", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                isExpanded = true
                                onStartScan()
                            }
                        ) {
                            Text("Scanner", fontSize = 12.sp)
                        }
                    }

                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Réduire" else "Déplier"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (devices.isEmpty()) {
                        Text(
                            text = if (isScanning) "Recherche d'appareils à proximité..." else "Aucun appareil trouvé. Cliquez sur Scanner.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(devices, key = { it.address }) { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.name,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${device.address}  •  ${device.rssi} dBm",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Button(
                                        onClick = { onConnectDevice(device.address) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Connecter", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItemRow(msg: DeviceMessage) {
    val isTx = msg.direction == MessageDirection.TX
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isTx) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isTx) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isTx) TxBadgeColor else RxBadgeColor)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (isTx) "TX (Envoyé)" else "RX (Reçu)",
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = msg.formattedTime,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isTx) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = msg.text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = if (isTx) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
