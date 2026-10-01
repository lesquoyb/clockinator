package com.example.alarmcontroller.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.Recurrence
import com.example.alarmcontroller.alarm.RingtoneSlot
import java.util.Locale

/**
 * Create / edit dialog for an [AlarmItem].
 *
 * The three ringtone slots (lock_phone, bedtime, wakeup) each expose:
 *   - time picker (native Android TimePickerDialog)
 *   - ring on phone toggle
 *   - ring on device toggle
 *
 * The BEDTIME slot additionally has an "enabled" toggle that collapses
 * the whole section when off (and merges it into lock_phone conceptually).
 */
@Composable
fun AlarmDialog(
    initialAlarm: AlarmItem? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        lockPhone: RingtoneSlot,
        bedtime: RingtoneSlot,
        wakeup: RingtoneSlot,
        recurrence: Recurrence,
        snoozeDurations: List<Int>,
        ringtoneFile: String?
    ) -> Unit
) {
    // ── Title ────────────────────────────────────────────────────────
    var title by remember { mutableStateOf(initialAlarm?.title ?: "Alarm") }

    // ── Slot states (stored directly as Int) ─────────────────────────
    var lpHour    by remember { mutableIntStateOf(initialAlarm?.lockPhone?.hour   ?: 22) }
    var lpMin     by remember { mutableIntStateOf(initialAlarm?.lockPhone?.minute  ?: 0) }
    var lpPhone   by remember { mutableStateOf(initialAlarm?.lockPhone?.ringOnPhone  ?: true) }
    var lpDevice  by remember { mutableStateOf(initialAlarm?.lockPhone?.ringOnDevice ?: true) }

    var btEnabled by remember { mutableStateOf(initialAlarm?.bedtime?.enabled ?: false) }
    var btHour    by remember { mutableIntStateOf(initialAlarm?.bedtime?.hour    ?: 23) }
    var btMin     by remember { mutableIntStateOf(initialAlarm?.bedtime?.minute   ?: 0) }
    var btPhone   by remember { mutableStateOf(initialAlarm?.bedtime?.ringOnPhone  ?: false) }
    var btDevice  by remember { mutableStateOf(initialAlarm?.bedtime?.ringOnDevice ?: true) }

    var wuHour    by remember { mutableIntStateOf(initialAlarm?.wakeup?.hour    ?: 7) }
    var wuMin     by remember { mutableIntStateOf(initialAlarm?.wakeup?.minute   ?: 0) }
    var wuPhone   by remember { mutableStateOf(initialAlarm?.wakeup?.ringOnPhone  ?: true) }
    var wuDevice  by remember { mutableStateOf(initialAlarm?.wakeup?.ringOnDevice ?: true) }

    // ── Recurrence ───────────────────────────────────────────────────
    var recMode by remember {
        mutableStateOf(
            when (initialAlarm?.recurrence) {
                is Recurrence.Weekly    -> "weekly"
                is Recurrence.EveryDays -> "interval"
                else                    -> "daily"
            }
        )
    }
    var selectedDays by remember {
        mutableStateOf(
            (initialAlarm?.recurrence as? Recurrence.Weekly)?.days ?: setOf(1, 2, 3, 4, 5)
        )
    }
    var intervalText by remember {
        mutableStateOf(
            ((initialAlarm?.recurrence as? Recurrence.EveryDays)?.interval ?: 2).toString()
        )
    }

    // ── Snooze durations ─────────────────────────────────────────────
    var snoozeDurations by remember {
        mutableStateOf(
            initialAlarm?.snoozeDurations?.toMutableList()
                ?: AlarmItem.DEFAULT_SNOOZE_DURATIONS.toMutableList()
        )
    }
    var newDurText by remember { mutableStateOf("") }

    // ── Ringtone file ─────────────────────────────────────────────────
    var ringtoneFile by remember { mutableStateOf(initialAlarm?.ringtoneFile ?: "") }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                if (initialAlarm == null) "New alarm" else "Edit alarm",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── Alarm name ────────────────────────────────────────
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Alarm name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // ── LOCK_PHONE slot ───────────────────────────────────
                SlotSection(
                    title        = "Lock-phone time",
                    subtitle     = "Phone locks & focus mode activates",
                    hour         = lpHour,
                    minute       = lpMin,
                    ringOnPhone  = lpPhone,
                    ringOnDevice = lpDevice,
                    enabled      = true,
                    showEnabledToggle = false,
                    onTimeChange   = { h, m -> lpHour = h; lpMin = m },
                    onPhoneChange  = { lpPhone  = it },
                    onDeviceChange = { lpDevice = it },
                    onEnabledChange = {}
                )

                HorizontalDivider()

                // ── BEDTIME slot (optional) ───────────────────────────
                SlotSection(
                    title        = "Bedtime",
                    subtitle     = "Optional. ESP LED lights up at this time.",
                    hour         = btHour,
                    minute       = btMin,
                    ringOnPhone  = btPhone,
                    ringOnDevice = btDevice,
                    enabled      = btEnabled,
                    showEnabledToggle = true,
                    onTimeChange   = { h, m -> btHour = h; btMin = m },
                    onPhoneChange  = { btPhone  = it },
                    onDeviceChange = { btDevice = it },
                    onEnabledChange = { btEnabled = it }
                )

                HorizontalDivider()

                // ── WAKEUP slot ───────────────────────────────────────
                SlotSection(
                    title        = "Wake-up time",
                    subtitle     = "Focus mode releases",
                    hour         = wuHour,
                    minute       = wuMin,
                    ringOnPhone  = wuPhone,
                    ringOnDevice = wuDevice,
                    enabled      = true,
                    showEnabledToggle = false,
                    onTimeChange   = { h, m -> wuHour = h; wuMin = m },
                    onPhoneChange  = { wuPhone  = it },
                    onDeviceChange = { wuDevice = it },
                    onEnabledChange = {}
                )

                HorizontalDivider()

                // ── Recurrence ────────────────────────────────────────
                SectionLabel("Recurrence")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = recMode == "daily",    onClick = { recMode = "daily" },    label = { Text("Daily") })
                    FilterChip(selected = recMode == "weekly",   onClick = { recMode = "weekly" },   label = { Text("Weekly") })
                    FilterChip(selected = recMode == "interval", onClick = { recMode = "interval" }, label = { Text("Every N days") })
                }
                if (recMode == "weekly") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { idx, lbl ->
                            FilterChip(
                                selected = idx + 1 in selectedDays,
                                onClick  = {
                                    val d = idx + 1
                                    selectedDays = if (d in selectedDays) selectedDays - d else selectedDays + d
                                },
                                label    = { Text(lbl) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (recMode == "interval") {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { if (it.length <= 3) intervalText = it },
                        label = { Text("Interval in days (1-365)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // ── Ringtone file ─────────────────────────────────────
                SectionLabel("Ringtone file (ESP32)")
                OutlinedTextField(
                    value = ringtoneFile,
                    onValueChange = { ringtoneFile = it },
                    label = { Text("Filename on device (e.g. alarm1.wav)") },
                    singleLine = true,
                    placeholder = { Text("Leave empty for built-in melody") },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // ── Snooze durations ──────────────────────────────────
                SectionLabel("Snooze durations (min)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    snoozeDurations.sorted().forEach { dur ->
                        InputChip(
                            selected = false, onClick = {},
                            label = { Text("${dur}m") },
                            trailingIcon = {
                                IconButton(
                                    onClick  = { snoozeDurations = snoozeDurations.toMutableList().also { it.remove(dur) } },
                                    modifier = Modifier.size(16.dp)
                                ) { Icon(Icons.Default.Close, null, Modifier.size(12.dp)) }
                            }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newDurText, onValueChange = { if (it.length <= 4) newDurText = it },
                        label = { Text("Add (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val v = newDurText.toIntOrNull()
                        if (v != null && v in 1..1440 && v !in snoozeDurations) {
                            snoozeDurations = (snoozeDurations + v).toMutableList()
                            newDurText = ""
                        }
                    }) { Icon(Icons.Default.Add, "Add duration") }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val recurrence = when (recMode) {
                    "weekly" -> {
                        if (selectedDays.isEmpty()) { errorMsg = "Select at least one day."; return@Button }
                        Recurrence.Weekly(selectedDays)
                    }
                    "interval" -> {
                        val iv = intervalText.toIntOrNull()
                        if (iv == null || iv !in 1..365) { errorMsg = "Interval must be 1-365 days."; return@Button }
                        Recurrence.EveryDays(iv)
                    }
                    else -> Recurrence.Daily
                }

                val finalDurations  = snoozeDurations.sorted().ifEmpty { AlarmItem.DEFAULT_SNOOZE_DURATIONS }
                val finalRingtoneFi = ringtoneFile.trim().ifEmpty { null }

                onSave(
                    title.ifBlank { "Alarm" },
                    RingtoneSlot(lpHour, lpMin, ringOnPhone = lpPhone, ringOnDevice = lpDevice),
                    RingtoneSlot(
                        if (btEnabled) btHour else lpHour,
                        if (btEnabled) btMin else lpMin,
                        ringOnPhone = btPhone,
                        ringOnDevice = btDevice,
                        enabled = btEnabled
                    ),
                    RingtoneSlot(wuHour, wuMin, ringOnPhone = wuPhone, ringOnDevice = wuDevice),
                    recurrence,
                    finalDurations,
                    finalRingtoneFi
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SlotSection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlotSection(
    title: String,
    subtitle: String,
    hour: Int,
    minute: Int,
    ringOnPhone: Boolean,
    ringOnDevice: Boolean,
    enabled: Boolean,
    showEnabledToggle: Boolean,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onPhoneChange: (Boolean) -> Unit,
    onDeviceChange: (Boolean) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
            if (showEnabledToggle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enabled) "On" else "Off", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
            }
        }

        if (enabled) {
            // Android Native TimePickerDialog Trigger Button
            Surface(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            onTimeChange(selectedHour, selectedMinute)
                        },
                        hour,
                        minute,
                        DateFormat.is24HourFormat(context)
                    ).show()
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Select time",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, selectedHour, selectedMinute ->
                                    onTimeChange(selectedHour, selectedMinute)
                                },
                                hour,
                                minute,
                                DateFormat.is24HourFormat(context)
                            ).show()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Pick time", fontSize = 12.sp)
                    }
                }
            }

            // Ring-on toggles
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Switch(checked = ringOnPhone, onCheckedChange = onPhoneChange)
                    Spacer(Modifier.width(6.dp))
                    Text("Phone", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Switch(checked = ringOnDevice, onCheckedChange = onDeviceChange)
                    Spacer(Modifier.width(6.dp))
                    Text("Device", fontSize = 13.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
}
