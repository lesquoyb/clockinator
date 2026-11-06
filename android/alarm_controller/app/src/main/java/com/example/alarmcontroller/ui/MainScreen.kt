package com.example.alarmcontroller.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmcontroller.MainActivity
import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.nfc.NfcActionHandler
import com.example.alarmcontroller.communication.ConnectionState
import com.example.alarmcontroller.communication.DeviceMessage
import com.example.alarmcontroller.communication.MessageDirection
import com.example.alarmcontroller.communication.ble.BleDeviceItem
import com.example.alarmcontroller.ui.theme.RxBadgeColor
import com.example.alarmcontroller.ui.theme.StatusConnected
import com.example.alarmcontroller.ui.theme.StatusConnecting
import com.example.alarmcontroller.ui.theme.StatusDisconnected
import com.example.alarmcontroller.ui.theme.TxBadgeColor
import kotlinx.coroutines.flow.collect

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val connectionState  by viewModel.connectionState.collectAsState()
    val wifiConnectionState by viewModel.wifiConnectionState.collectAsState()
    val alarms           by viewModel.alarms.collectAsState()
    val hasPendingSync   by viewModel.hasPendingSync.collectAsState()
    val isPhoneRinging   by viewModel.isPhoneAlarmRinging.collectAsState()
    val ringingTitle     by viewModel.ringingAlarmTitle.collectAsState()
    val statusAlert      by viewModel.statusAlert.collectAsState()
    val wifiError = (wifiConnectionState as? ConnectionState.Error)?.message
    var dismissedWifiError by remember(wifiError) { mutableStateOf(false) }

    val isConnected = connectionState is ConnectionState.Connected
    val connectionLabel = when (connectionState) {
        is ConnectionState.Connected -> "Connected · ${(connectionState as ConnectionState.Connected).deviceName}"
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Error -> "Connection error"
        ConnectionState.Disconnected -> "Disconnected"
    }
    val connectionColor = when (connectionState) {
        is ConnectionState.Connected -> StatusConnected
        is ConnectionState.Connecting -> StatusConnecting
        is ConnectionState.Error -> StatusDisconnected
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
    }
    val wifiConnectionColor = when (wifiConnectionState) {
        is ConnectionState.Connected -> StatusConnected
        is ConnectionState.Connecting -> StatusConnecting
        is ConnectionState.Error -> StatusDisconnected
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
    }
    val wifiConnectionLabel = when (wifiConnectionState) {
        is ConnectionState.Connected -> "connected"
        is ConnectionState.Connecting -> "connecting"
        is ConnectionState.Error -> (wifiConnectionState as ConnectionState.Error).message
        ConnectionState.Disconnected -> "disconnected"
    }
    var currentPage by rememberSaveable {
        mutableStateOf(if (viewModel.isSetupRequired) "setup_device" else "home")
    }
    var editorOrigin by rememberSaveable { mutableStateOf("home") }
    var editingAlarm    by remember { mutableStateOf<AlarmItem?>(null) }

    fun finishSetup() {
        viewModel.completeSetup()
        viewModel.stopScan()
        currentPage = "home"
    }

    fun openAlarmEditor(alarm: AlarmItem? = null) {
        editingAlarm = alarm
        editorOrigin = currentPage
        currentPage = "alarm_editor"
    }

    fun closeAlarmEditor() {
        currentPage = editorOrigin
        editingAlarm = null
    }

    BackHandler(enabled = currentPage != "home" && currentPage != "setup_device") {
        currentPage = when (currentPage) {
            "setup_alarm" -> "setup_device"
            "alarm_editor" -> editorOrigin
            else -> "home"
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage != "setup_device") viewModel.stopScan()
    }

    // Focus mode onboarding bottom sheet, launched with Activity context via LocalContext
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.nfcToastMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(statusAlert) {
        statusAlert?.let {
            snackbarState.showSnackbar(it)
            viewModel.clearAlert()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            if (currentPage == "home" && alarms.isNotEmpty()) {
                FloatingActionButton(onClick = { openAlarmEditor() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add routine")
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    when (currentPage) {
                        "setup_device" -> Text("Set up your alarm")
                        "setup_alarm" -> Text("Your first routine")
                        "home" -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Clockinator", style = MaterialTheme.typography.titleLarge)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(connectionColor))
                                    Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth $connectionLabel",
                                        modifier = Modifier.size(14.dp), tint = connectionColor)
                                    Text("BT", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(wifiConnectionColor))
                                    Icon(Icons.Default.Wifi, contentDescription = "Wi-Fi $wifiConnectionLabel",
                                        modifier = Modifier.size(14.dp), tint = wifiConnectionColor)
                                    Text("Wi-Fi", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (hasPendingSync) {
                                    Text("Sync pending", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                        "settings" -> Text("More settings")
                        "device" -> Text("Alarm clock")
                        else -> Text(if (editingAlarm == null) "New routine" else "Edit routine")
                    }
                },
                navigationIcon = {
                    if (currentPage != "home" && currentPage != "setup_device") {
                        IconButton(onClick = {
                            currentPage = when (currentPage) {
                                "setup_alarm" -> "setup_device"
                                "alarm_editor" -> editorOrigin
                                else -> "home"
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    when (currentPage) {
                        "home" -> {
                            IconButton(onClick = { currentPage = "device" }) {
                                Icon(
                                    if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                    contentDescription = "Alarm clock connection: $connectionLabel"
                                )
                            }
                            IconButton(onClick = { currentPage = "settings" }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                        "setup_device", "setup_alarm" -> TextButton(onClick = { finishSetup() }) {
                            Text("Set up later")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {
            AnimatedVisibility(visible = isPhoneRinging) {
                RingingBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    title = ringingTitle ?: "Alarm",
                    snoozeDurations = viewModel.currentSnoozeDurations(),
                    onStop = { viewModel.stopPhoneAlarm() },
                    onSnooze = { viewModel.snoozePhoneAlarm(it) }
                )
            }
            if (wifiError != null && !dismissedWifiError) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text("ESP32 Wi-Fi connection failed", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            Text(wifiError, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { dismissedWifiError = true }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss Wi-Fi error")
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            when (currentPage) {
                "setup_device" -> SetupDeviceScreen(
                    hasPermissions = hasPermissions,
                    connectionState = connectionState,
                    devices = viewModel.scannedDevices.collectAsState().value,
                    isScanning = viewModel.isScanning.collectAsState().value,
                    onRequestPermissions = onRequestPermissions,
                    onStartScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() },
                    onConnect = { address, ssid, password -> viewModel.connectDevice(address, ssid, password) },
                    onContinue = { viewModel.stopScan(); currentPage = "setup_alarm" }
                )
                "setup_alarm" -> SetupAlarmScreen(
                    onCreateAlarm = { openAlarmEditor() }
                )
                "home" -> {
                    AlarmsTab(
                        alarms = alarms,
                        onAdd = { openAlarmEditor() },
                        onEdit = { openAlarmEditor(it) },
                        onToggle = { alarm, enabled -> viewModel.toggleAlarm(alarm, enabled) },
                        onDismissNext = { alarm, type -> viewModel.toggleDismissNext(alarm, type) },
                        onDismissRoutineOnce = { viewModel.dismissRoutineOnce(it) },
                        onDelete = { viewModel.deleteAlarm(it) }
                    )
                }
                "settings" -> SettingsTab(
                    viewModel = viewModel,
                    isConnected = isConnected,
                    onWriteNfcTag = { payload -> (context as? MainActivity)?.beginNfcTagWrite(payload) },
                    onOpenDevice = { currentPage = "device" }
                )
                "device" -> DeviceTab(
                    viewModel = viewModel,
                    hasPermissions = hasPermissions,
                    onRequestPerms = onRequestPermissions,
                    connectionState = connectionState,
                    scannedDevices = viewModel.scannedDevices.collectAsState().value,
                    isScanning = viewModel.isScanning.collectAsState().value,
                    messages = viewModel.messages.collectAsState().value,
                    isConnected = isConnected
                )
                "alarm_editor" -> AlarmDialog(
                    initialAlarm = editingAlarm,
                    onDismiss = { closeAlarmEditor() },
                    onSave = { title, lockPhone, bedtime, wakeup, recurrence, snoozeDurations, ringtoneFile ->
                        viewModel.addOrUpdateAlarm(
                            id = editingAlarm?.id,
                            title = title,
                            lockPhone = lockPhone,
                            bedtime = bedtime,
                            wakeup = wakeup,
                            recurrence = recurrence,
                            snoozeDurations = snoozeDurations,
                            ringtoneFile = ringtoneFile
                        )
                        if (editorOrigin == "setup_alarm") finishSetup() else closeAlarmEditor()
                    }
                )
            }
        }
        }

    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ringing banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RingingBanner(
    modifier: Modifier = Modifier,
    title: String,
    snoozeDurations: List<Int>,
    onStop: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStop,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop alarm")
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Snooze", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(snoozeDurations.sorted()) { dur ->
                        OutlinedButton(
                            onClick = { onSnooze(dur) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("${dur} min") }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 0:  Alarms
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AlarmsTab(
    alarms: List<AlarmItem>,
    onAdd: () -> Unit,
    onEdit: (AlarmItem) -> Unit,
    onToggle: (AlarmItem, Boolean) -> Unit,
    onDismissNext: (AlarmItem, com.example.alarmcontroller.alarm.RingtoneType) -> Unit,
    onDismissRoutineOnce: (AlarmItem) -> Unit,
    onDelete: (AlarmItem) -> Unit
) {
    if (alarms.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Alarm, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("No routines yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("Set up Focus, Bedtime and Wake-up in one routine.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create a routine")
                }
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 96.dp)
        ) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text("Routines", style = MaterialTheme.typography.headlineSmall)
                    Text("${alarms.count { it.isEnabled }} active · ${alarms.size} routines",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(alarm, onEdit = { onEdit(alarm) }, onToggle = { onToggle(alarm, it) },
                    onDismissNext = { rt -> onDismissNext(alarm, rt) },
                    onDismissRoutineOnce = { onDismissRoutineOnce(alarm) },
                    onDelete = { onDelete(alarm) })
            }
        }
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmItem,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDismissNext: (com.example.alarmcontroller.alarm.RingtoneType) -> Unit,
    onDismissRoutineOnce: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    val focusType = com.example.alarmcontroller.alarm.RingtoneType.LOCK_PHONE
    val bedtimeType = com.example.alarmcontroller.alarm.RingtoneType.BEDTIME
    val wakeupType = com.example.alarmcontroller.alarm.RingtoneType.WAKEUP
    val nextRingtoneType = alarm.nextRingtoneType()
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!alarm.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                             else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(alarm.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(if (alarm.isEnabled) "Routine on" else "Routine off",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { enabled -> if (enabled) onToggle(true) else confirmDisable = true }
                )
            }
            Text(alarm.recurrence.summary(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            RoutineEventRow(
                label = "Focus",
                time = alarm.lockPhoneFormatted,
                phoneOn = true,
                deviceOn = alarm.lockPhone.ringOnDevice,
                skipped = alarm.isDismissed(focusType),
                emphasized = false,
                enabled = alarm.isEnabled,
                onUndoSkip = { onDismissNext(focusType) }
            )
            if (alarm.isBedtimeActive) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                RoutineEventRow(
                    label = "Bedtime",
                    time = alarm.bedtimeFormatted,
                    phoneOn = alarm.bedtime.ringOnPhone,
                    deviceOn = alarm.bedtime.ringOnDevice,
                    skipped = alarm.isDismissed(bedtimeType),
                    emphasized = false,
                    enabled = alarm.isEnabled,
                    onUndoSkip = { onDismissNext(bedtimeType) }
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            RoutineEventRow(
                label = "Wake-up",
                time = alarm.wakeupFormatted,
                phoneOn = alarm.wakeup.ringOnPhone,
                deviceOn = true,
                skipped = alarm.isDismissed(wakeupType),
                emphasized = true,
                enabled = alarm.isEnabled,
                onUndoSkip = { onDismissNext(wakeupType) }
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                if (alarm.isEnabled) {
                    FilledTonalButton(
                        onClick = {
                            if (nextRingtoneType != null) onDismissNext(nextRingtoneType)
                            else alarm.dismissedRingtones.forEach(onDismissNext)
                        },
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(if (nextRingtoneType == null) Icons.Default.Replay else Icons.Default.SkipNext,
                            contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(nextRingtoneType?.let(::skipLabel) ?: "Undo skips")
                    }
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete alarm",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Pause ${alarm.title}?") },
            text = { Text("Dismiss once skips the next Focus, Bedtime (if enabled), and Wake-up events. The routine stays on afterward.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { confirmDisable = false; onDismissRoutineOnce() }) {
                        Text("Dismiss once")
                    }
                    TextButton(onClick = { confirmDisable = false; onToggle(false) }) {
                        Text("Until further notice")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${alarm.title}?") },
            text = { Text("This routine will be removed from your phone and Clockinator.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SlotRow(
    label: String,
    time: String,
    ringOnPhone: Boolean,
    ringOnDevice: Boolean,
    dismissed: Boolean,
    enabled: Boolean,
    onDismissToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    time,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                if (ringOnPhone) {
                    Icon(Icons.Default.PhoneAndroid, "Rings on phone", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                if (ringOnDevice) {
                    Icon(Icons.Default.Devices, "Rings on connected device", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        if (enabled) {
            if (dismissed) {
                AssistChip(
                    onClick = onDismissToggle,
                    label = { Text("Skipped") },
                    leadingIcon = { Icon(Icons.Default.AlarmOff, null, Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                        leadingIconContentColor = MaterialTheme.colorScheme.error
                    )
                )
            } else {
                AssistChip(
                    onClick = onDismissToggle,
                    label = { Text("Skip once") },
                    leadingIcon = { Icon(Icons.Default.Alarm, null, Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
private fun RoutineEventRow(
    label: String,
    time: String,
    phoneOn: Boolean,
    deviceOn: Boolean,
    skipped: Boolean,
    emphasized: Boolean,
    enabled: Boolean,
    onUndoSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall,
                    color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (skipped) {
                    Text("Skipped next", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Text(time, style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            if (skipped) {
                TextButton(onClick = onUndoSkip, contentPadding = PaddingValues(0.dp)) {
                    Text("Undo skip")
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (phoneOn) OutputIndicator("Phone", Icons.Default.PhoneAndroid)
            if (deviceOn) OutputIndicator("Alarm clock", Icons.Default.Alarm)
        }
    }
}

private fun skipLabel(type: com.example.alarmcontroller.alarm.RingtoneType): String = when (type) {
    com.example.alarmcontroller.alarm.RingtoneType.LOCK_PHONE -> "Skip Focus"
    com.example.alarmcontroller.alarm.RingtoneType.BEDTIME -> "Skip Bedtime"
    com.example.alarmcontroller.alarm.RingtoneType.WAKEUP -> "Skip Wake-up"
}

@Composable
private fun OutputIndicator(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1: Device (BLE + remote commands)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DeviceTab(
    viewModel: MainViewModel,
    hasPermissions: Boolean,
    onRequestPerms: () -> Unit,
    connectionState: ConnectionState,
    scannedDevices: List<BleDeviceItem>,
    isScanning: Boolean,
    messages: List<DeviceMessage>,
    isConnected: Boolean
) {
    val listState = rememberLazyListState()
    val wifiState by viewModel.wifiConnectionState.collectAsState()
    val canSendToDevice = isConnected || wifiState is ConnectionState.Connected
    var textToSend by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
            Text("Device", style = MaterialTheme.typography.headlineSmall)
            Text("Connect and manage your Clockinator", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (!hasPermissions) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth access needed", fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    TextButton(onClick = onRequestPerms) { Text("Grant") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        ConnectionCard(connectionState, onDisconnect = { viewModel.disconnect() })
        Spacer(Modifier.height(10.dp))

        ScanSection(isScanning, isConnected, scannedDevices, { viewModel.startScan() }, { viewModel.stopScan() }, { viewModel.connectDevice(it) })
        Spacer(Modifier.height(10.dp))

        if (canSendToDevice) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remote controls", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.stopCurrentAlarm() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                        Button(onClick = { viewModel.pushSyncToDevice() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Sync alarms")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Snooze", style = MaterialTheme.typography.labelMedium)
                        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(listOf(5, 10, 30, 60)) { duration ->
                                AssistChip(onClick = { viewModel.snoozeCurrentAlarm(duration) },
                                    label = { Text("${duration}m") })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Activity", style = MaterialTheme.typography.titleSmall)
            Text("${messages.size} messages", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(
            Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp, bottom = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No messages yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(messages, key = { it.id }) { MessageRow(it) }
                }
            }
        }

        // Send bar
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = textToSend, onValueChange = { textToSend = it },
                modifier = Modifier.weight(1f), placeholder = { Text("Send command…") },
                singleLine = true, enabled = canSendToDevice
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.sendMessage(textToSend); textToSend = "" },
                enabled = canSendToDevice && textToSend.isNotBlank(),
                modifier = Modifier.size(48.dp).background(
                    if (canSendToDevice && textToSend.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant, CircleShape
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send",
                    tint = if (canSendToDevice && textToSend.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 2 — Settings
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsTab(
    viewModel: MainViewModel,
    isConnected: Boolean,
    onWriteNfcTag: (String) -> Unit,
    onOpenDevice: () -> Unit
) {
    val volumeLevel by viewModel.ringtoneVolumeLevel.collectAsState()
    val confirmedVolume by viewModel.confirmedVolumeLevel.collectAsState()
    val clockLightOn by viewModel.clockLightOn.collectAsState()
    val clockLightColor by viewModel.clockLightColor.collectAsState()
    var lightRed by rememberSaveable(clockLightColor) { mutableStateOf(clockLightColor?.get(0)?.toString() ?: "255") }
    var lightGreen by rememberSaveable(clockLightColor) { mutableStateOf(clockLightColor?.get(1)?.toString() ?: "255") }
    var lightBlue by rememberSaveable(clockLightColor) { mutableStateOf(clockLightColor?.get(2)?.toString() ?: "255") }
    val isVolumeTestPlaying by viewModel.isVolumeTestPlaying.collectAsState()
    val lastEspWifiIp by viewModel.lastEspWifiIp.collectAsState()
    val autoSyncHours     by viewModel.autoSyncHours.collectAsState()
    val backgroundSync by viewModel.backgroundSyncEnabled.collectAsState()
    val displayRefreshMinutes by viewModel.displayRefreshMinutes.collectAsState()
    val wifiSync by viewModel.wifiSyncEnabled.collectAsState()
    val clockStatus by viewModel.clockStatusEnabled.collectAsState()
    val uploadedRingtones by viewModel.uploadedRingtones.collectAsState()
    val uploadState       by viewModel.uploadState.collectAsState()
    val isLocked          by viewModel.lockModeManager.isLocked.collectAsState()
    val wifiConnectionState by viewModel.wifiConnectionState.collectAsState()
    val canSendToDevice = isConnected || wifiConnectionState is ConnectionState.Connected

    // Activity context is required to start system settings intents on Android 10+
    val context = androidx.compose.ui.platform.LocalContext.current

    // File picker for WAV upload
    val wavPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // Extract filename from URI, sanitise to alphanumeric + underscore
            val raw = uri.lastPathSegment?.substringAfterLast('/') ?: "ringtone.wav"
            val safe = raw.replace(Regex("[^a-zA-Z0-9_.]"), "_")
            viewModel.uploadRingtoneToDevice(uri, safe)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Text("Personalise alarms and your connected device", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(title = "Appearance") {
            com.example.alarmcontroller.ui.theme.AppearanceSettings()
        }

        SettingsSection(title = "Alarm clock") {
            Text(if (canSendToDevice) "Connected and syncing alarms" else "Not connected",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onOpenDevice) { Text("Manage connection") }
        }

        SettingsSection(title = "Clock ringtone volume") {
            ClockVolumeControl(volumeLevel, confirmedVolume, canSendToDevice, viewModel::setRingtoneVolume,
                uploadedRingtones.sorted(), isVolumeTestPlaying,
                viewModel::testClockRingtone, viewModel::stopClockRingtoneTest)
        }

        var wifiSsid by rememberSaveable { mutableStateOf("") }
        var wifiPassword by rememberSaveable { mutableStateOf("") }
        SettingsSection(title = "Local Wi-Fi") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Wi-Fi connection and sync", modifier = Modifier.weight(1f))
                Switch(checked = wifiSync, onCheckedChange = viewModel::setWifiSyncEnabled)
            }
            Text(
                when (wifiConnectionState) {
                    is ConnectionState.Connected -> "ESP32 reachable over Wi-Fi"
                    is ConnectionState.Connecting -> "Connecting to ESP32 over Wi-Fi…"
                    is ConnectionState.Error -> "Wi-Fi unavailable: ${(wifiConnectionState as ConnectionState.Error).message}"
                    ConnectionState.Disconnected -> "Not connected over Wi-Fi; Bluetooth remains available"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WifiCredentialsFields(
                ssid = wifiSsid,
                onSsidChange = { wifiSsid = it },
                password = wifiPassword,
                onPasswordChange = { wifiPassword = it },
                showTitle = false
            )
            Button(
                onClick = { viewModel.configureWifi(wifiSsid, wifiPassword) },
                enabled = wifiSync && isConnected && wifiSsid.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send Wi-Fi settings to clock")
            }
            OutlinedButton(
                onClick = { viewModel.retryWifiConnection() },
                enabled = wifiSync && lastEspWifiIp != null && wifiConnectionState !is ConnectionState.Connecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry Wi-Fi connection")
            }
            if (!isConnected) {
                Text("Connect to the clock over Bluetooth before changing its Wi-Fi network.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── NFC Tags ─────────────────────────────────────────────────
        SettingsSection(title = "NFC Tags") {
            Text(
                "Program physical NFC tags to control your alarms hands-free. " +
                "Place an ODODO tag where you leave your phone outside the bedroom, and an OBOULO tag where you want to \"validate\" that you are awake.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            NfcTagConfigRow(
                label       = "ODODO tag",
                description = "Stops the bedtime alarm & activates focus mode.",
                payload     = NfcActionHandler.TAG_PAYLOAD_ODODO,
                onWrite     = { onWriteNfcTag(NfcActionHandler.TAG_PAYLOAD_ODODO) }
            )
            Spacer(Modifier.height(6.dp))
            NfcTagConfigRow(
                label       = "OBOULO tag",
                description = "Stops the wake-up alarm & releases focus mode",
                payload     = NfcActionHandler.TAG_PAYLOAD_OBOULO,
                onWrite     = { onWriteNfcTag(NfcActionHandler.TAG_PAYLOAD_OBOULO) }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Default payloads (\"ODODO\" / \"OBOULO\") work out of the box on any blank NFC tag. " +
                "Tap 'Write' to program a tag, then tap it with your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // ── Focus / Lock Mode ─────────────────────────────────────────
        SettingsSection(title = "Focus Mode") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Status", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(if (isLocked) "Active: DND on, display dimmed" else "Inactive",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isLocked)
                    Button(onClick = { viewModel.lockModeManager.release() }) { Text("Release") }
            }
            Spacer(Modifier.height(8.dp))
            // DND permission
            if (!viewModel.lockModeManager.hasDndPermission()) {
                OutlinedButton(onClick = { context.startActivity(viewModel.dndSettingsIntent()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.NotificationsOff, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Grant Do Not Disturb access", fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
            }
            OutlinedButton(onClick = { context.startActivity(viewModel.focusModeIntent()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhoneAndroid, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Set up app blocking in Focus Mode", fontSize = 13.sp)
            }
        }

        // ── Ringtones ─────────────────────────────────────────────────
        SettingsSection(title = "Device ringtones") {
            Text("Upload WAV files to the ESP32 filesystem. Assign a file to each alarm when editing it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            // Upload progress
            when (val state = uploadState) {
                is MainViewModel.UploadState.InProgress -> {
                    Column {
                        Text("Uploading ${state.filename}…", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
                is MainViewModel.UploadState.Error ->
                    Text("Upload error: ${state.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else -> {}
            }

            Button(
                onClick  = { wavPicker.launch("audio/wav") },
                enabled  = canSendToDevice && uploadState !is MainViewModel.UploadState.InProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Upload, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Upload WAV to device")
            }

            if (uploadedRingtones.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Files on device:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                uploadedRingtones.sorted().forEach { filename ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(filename, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.deleteRingtoneFromDevice(filename) }) {
                            Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // ── Auto-sync ─────────────────────────────────────────────────
        Text("Clockinator " + com.example.alarmcontroller.BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsSection(title = "Clock light") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Light")
                    Text(if (clockLightOn == null && canSendToDevice) "Waiting for clock state…" else "Use the configured RGB color",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = clockLightOn == true,
                    onCheckedChange = viewModel::setClockLight,
                    enabled = canSendToDevice && clockLightOn != null)
            }
            Text("RGB values: 0–255 per channel. Apply while the light is on to preview.",
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Red", lightRed, { value: String -> lightRed = value }),
                    Triple("Green", lightGreen, { value: String -> lightGreen = value }),
                    Triple("Blue", lightBlue, { value: String -> lightBlue = value })).forEach { (label, value, update) ->
                    OutlinedTextField(value = value, onValueChange = update,
                        label = { Text(label) }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        isError = value.toIntOrNull()?.let { it !in 0..255 } ?: true,
                        enabled = canSendToDevice && clockLightColor != null)
                }
            }
            val rgb = listOf(lightRed, lightGreen, lightBlue).map { it.toIntOrNull() }
            OutlinedButton(onClick = {
                val red = lightRed.toIntOrNull()
                val green = lightGreen.toIntOrNull()
                val blue = lightBlue.toIntOrNull()
                if (red != null && green != null && blue != null)
                    viewModel.setClockLightColor(red, green, blue)
            }, enabled = canSendToDevice && clockLightColor != null && rgb.all { it != null && it in 0..255 },
                modifier = Modifier.fillMaxWidth()) {
                Text("Apply RGB")
            }
        }
        SettingsSection(title = "Clock screen") {
            OutlinedButton(onClick = viewModel::refreshClockDisplay, enabled = canSendToDevice,
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Full screen refresh now")
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Periodic full refresh")
                    Text("Off by default. The clock cleans the screen once after its first sync.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = displayRefreshMinutes > 0,
                    onCheckedChange = { viewModel.setDisplayRefreshMinutes(if (it) 30 else 0) })
            }
            if (displayRefreshMinutes > 0) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(10, 30, 60, 120)) { minutes ->
                        FilterChip(selected = displayRefreshMinutes == minutes,
                            onClick = { viewModel.setDisplayRefreshMinutes(minutes) },
                            label = { Text("${minutes} min") })
                    }
                }
            }
        }

        SettingsSection(title = "Auto-sync") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Background checks")
                    Text("Silent checks about every 15 minutes; Android may delay them.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = backgroundSync, onCheckedChange = viewModel::setBackgroundSyncEnabled)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Persistent clock status")
                    Text("Keeps the connection active with a silent status notification.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = clockStatus, onCheckedChange = viewModel::setClockStatusEnabled)
            }
            Text("Automatically push alarms to the device every N hours. If the device is unreachable the sync is queued and retried on next connection.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(0 to "Off", 1 to "1h", 2 to "2h", 6 to "6h", 12 to "12h", 24 to "24h")) { (hours, label) ->
                    FilterChip(selected = autoSyncHours == hours, onClick = { viewModel.setAutoSyncHours(hours) },
                        label = { Text(label) })
                }
            }
        }

    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NFC tag config row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NfcTagConfigRow(label: String, description: String, payload: String, onWrite: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Payload: \"$payload\"", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
        }
        OutlinedButton(
            onClick = onWrite,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Nfc, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Write", fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Focus Mode onboarding bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeOnboardingSheet(
    hasDndPermission: Boolean,
    onOpenDndSettings: () -> Unit,
    onOpenFocusMode: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Set up Focus Mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Scanning the NFC tag can silence notifications and pause the apps you choose.\n\n" +
                "Allow Do Not Disturb access to silence notifications. Use Digital Wellbeing to choose which apps are paused. " +
                "You can always take a break when you need access.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (!hasDndPermission) {
                OutlinedButton(onClick = onOpenDndSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.NotificationsOff, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Allow Do Not Disturb control")
                }
            }

            Button(onClick = onOpenFocusMode, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhoneAndroid, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open Digital Wellbeing")
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable small composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
fun PillBadge(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp, color = fg)
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        content()
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun ConnectionCard(connectionState: ConnectionState, onDisconnect: () -> Unit) {
    val isConnected = connectionState is ConnectionState.Connected
    val accent = when (connectionState) {
        is ConnectionState.Connected -> StatusConnected
        is ConnectionState.Connecting -> StatusConnecting
        is ConnectionState.Error -> StatusDisconnected
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
    }
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (connectionState) {
                        is ConnectionState.Connected -> "Connected to ${connectionState.deviceName}"
                        is ConnectionState.Connecting -> "Connecting to device"
                        is ConnectionState.Error -> "Connection failed"
                        ConnectionState.Disconnected -> "No device connected"
                    },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when (connectionState) {
                        is ConnectionState.Connected -> connectionState.address
                        is ConnectionState.Connecting -> "Establishing Bluetooth connection…"
                        is ConnectionState.Error -> connectionState.message
                        ConnectionState.Disconnected -> "Scan nearby devices to connect"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connectionState is ConnectionState.Error) accent
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isConnected) {
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Default.LinkOff, contentDescription = "Disconnect device", tint = accent)
                }
            }
        }
    }
}

@Composable
fun ScanSection(
    isScanning: Boolean, isConnected: Boolean, devices: List<BleDeviceItem>,
    onStartScan: () -> Unit, onStopScan: () -> Unit, onConnect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(!isConnected) }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Nearby devices", style = MaterialTheme.typography.titleSmall)
                        Text("${devices.size} found", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Hide nearby devices" else "Show nearby devices")
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    if (isScanning) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                    if (devices.isEmpty()) {
                        Text(if (isScanning) "Searching for nearby devices…" else "Start a scan to find your device.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(devices.sortedByDescending { deviceItem -> deviceItem.rssi }, key = { it.address }) { device ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("${device.address} · ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                    }
                                    OutlinedButton(onClick = { onConnect(device.address) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                        Text("Connect")
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (isScanning) {
                        OutlinedButton(onClick = onStopScan, modifier = Modifier.fillMaxWidth()) { Text("Stop scan") }
                    } else {
                        Button(onClick = { onStartScan(); expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan for devices")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageRow(msg: DeviceMessage) {
    val isTx = msg.direction == MessageDirection.TX
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isTx) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isTx) TxBadgeColor.copy(alpha = 0.18f) else RxBadgeColor.copy(alpha = 0.18f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(msg.text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                color = if (isTx) TxBadgeColor else RxBadgeColor)
        }
    }
}
