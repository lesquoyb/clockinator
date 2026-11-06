package com.example.alarmcontroller.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
 * Create / edit a wake-up routine represented by an [AlarmItem].
 *
    * The three routine events (focus, bedtime, wakeup) each expose:
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
    availableRingtones: Set<String> = emptySet(),
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
    var title by rememberSaveable { mutableStateOf(initialAlarm?.title ?: "Routine") }

    // ── Slot states (stored directly as Int) ─────────────────────────
    var lpHour    by rememberSaveable { mutableIntStateOf(initialAlarm?.lockPhone?.hour   ?: 22) }
    var lpMin     by rememberSaveable { mutableIntStateOf(initialAlarm?.lockPhone?.minute  ?: 0) }
    var lpPhone   by rememberSaveable { mutableStateOf(true) }
    var lpDevice  by rememberSaveable { mutableStateOf(initialAlarm?.lockPhone?.ringOnDevice ?: true) }

    var btEnabled by rememberSaveable { mutableStateOf(initialAlarm?.bedtime?.enabled ?: false) }
    var btHour    by rememberSaveable { mutableIntStateOf(initialAlarm?.bedtime?.hour    ?: 23) }
    var btMin     by rememberSaveable { mutableIntStateOf(initialAlarm?.bedtime?.minute   ?: 0) }
    val bedtimeNeedsOutput = initialAlarm?.bedtime?.let {
        it.enabled && !it.ringOnPhone && !it.ringOnDevice
    } ?: false
    var btPhone   by rememberSaveable { mutableStateOf(initialAlarm?.bedtime?.ringOnPhone ?: bedtimeNeedsOutput) }
    var btDevice  by rememberSaveable { mutableStateOf(initialAlarm?.bedtime?.ringOnDevice ?: !bedtimeNeedsOutput) }

    var wuHour    by rememberSaveable { mutableIntStateOf(initialAlarm?.wakeup?.hour    ?: 7) }
    var wuMin     by rememberSaveable { mutableIntStateOf(initialAlarm?.wakeup?.minute   ?: 0) }
    var wuPhone   by rememberSaveable { mutableStateOf(initialAlarm?.wakeup?.ringOnPhone  ?: true) }
    var wuDevice  by rememberSaveable { mutableStateOf(true) }

    // ── Recurrence ───────────────────────────────────────────────────
    var recMode by rememberSaveable {
        mutableStateOf(
            when (initialAlarm?.recurrence) {
                is Recurrence.Weekly    -> "weekly"
                is Recurrence.EveryDays -> "interval"
                else                    -> "daily"
            }
        )
    }
    var selectedDays by rememberSaveable {
        mutableStateOf(
            (initialAlarm?.recurrence as? Recurrence.Weekly)?.days ?: setOf(1, 2, 3, 4, 5)
        )
    }
    var intervalText by rememberSaveable {
        mutableStateOf(
            ((initialAlarm?.recurrence as? Recurrence.EveryDays)?.interval ?: 2).toString()
        )
    }

    // ── Snooze durations ─────────────────────────────────────────────
    var snoozeDurations by rememberSaveable {
        mutableStateOf(
            initialAlarm?.snoozeDurations?.toMutableList()
                ?: AlarmItem.DEFAULT_SNOOZE_DURATIONS.toMutableList()
        )
    }
    var newDurText by rememberSaveable { mutableStateOf("") }

    // ── Ringtone file ─────────────────────────────────────────────────
    var ringtoneFile by rememberSaveable { mutableStateOf(initialAlarm?.ringtoneFile ?: "") }

    var showNightOptions by rememberSaveable { mutableStateOf(false) }
    var errorMsg by rememberSaveable { mutableStateOf<String?>(null) }

    fun snapshot() = listOf(title, lpHour, lpMin, lpDevice, btEnabled, btHour, btMin,
        btPhone, btDevice, wuHour, wuMin, wuPhone, recMode, selectedDays.sorted(),
        intervalText, snoozeDurations.toList(), ringtoneFile).toString()
    val initialSnapshot = rememberSaveable(initialAlarm?.id) { snapshot() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    fun requestClose() {
        if (snapshot() != initialSnapshot) confirmDiscard = true else onDismiss()
    }
    BackHandler { requestClose() }
    if (confirmDiscard) {
        AlertDialog(onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your routine has not been saved.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
    }
    var chooseSound by rememberSaveable { mutableStateOf(false) }
    if (chooseSound) {
        AlertDialog(onDismissRequest = { chooseSound = false }, title = { Text("Clock sound") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    item {
                        TextButton(onClick = { ringtoneFile = ""; chooseSound = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (ringtoneFile.isBlank()) "✓ Built-in melody" else "Built-in melody")
                        }
                    }
                    items(availableRingtones.sorted()) { file ->
                        TextButton(onClick = { ringtoneFile = file; chooseSound = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (ringtoneFile == file) "✓ $file" else file)
                        }
                    }
                    if (availableRingtones.isEmpty()) item { Text("Upload WAV files in Clock → Device ringtones.") }
                }
            }, confirmButton = { TextButton(onClick = { chooseSound = false }) { Text("Done") } })
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

                // ── Alarm name ────────────────────────────────────────
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Routine name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("Focus")
                SlotSection(
                    title = "Focus start",
                    subtitle = "Phone focus mode and smart clock",
                    hour = lpHour,
                    minute = lpMin,
                    ringOnPhone = true,
                    ringOnDevice = lpDevice,
                    enabled = true,
                    showEnabledToggle = false,
                    phoneRequired = true,
                    onTimeChange = { h, m -> lpHour = h; lpMin = m },
                    onPhoneChange = {},
                    onDeviceChange = { lpDevice = it },
                    onEnabledChange = {}
                )
                HorizontalDivider()
                SlotSection(
                    title = "Bedtime",
                    subtitle = "Optional, but at least one sound output is required",
                    hour = btHour,
                    minute = btMin,
                    ringOnPhone = btPhone,
                    ringOnDevice = btDevice,
                    enabled = btEnabled,
                    showEnabledToggle = true,
                    requireOneOutput = true,
                    onTimeChange = { h, m -> btHour = h; btMin = m },
                    onPhoneChange = { btPhone = it },
                    onDeviceChange = { btDevice = it },
                    onEnabledChange = {
                        btEnabled = it
                        if (it && !btPhone && !btDevice) btPhone = true
                    }
                )

                SectionLabel("Wake-up")
                SlotSection(
                    title = "Wake-up time",
                    subtitle = "The alarm clock always sounds",
                    hour = wuHour,
                    minute = wuMin,
                    ringOnPhone = wuPhone,
                    ringOnDevice = true,
                    enabled = true,
                    showEnabledToggle = false,
                    deviceRequired = true,
                    onTimeChange = { h, m -> wuHour = h; wuMin = m },
                    onPhoneChange = { wuPhone = it },
                    onDeviceChange = {},
                    onEnabledChange = {}
                )

                SectionLabel("Repeats")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("daily", "weekly", "interval")) { mode ->
                        FilterChip(
                            selected = recMode == mode,
                            onClick = { recMode = mode },
                            label = { Text(when (mode) {
                                "daily" -> "Daily"
                                "weekly" -> "Weekly"
                                else -> "Every N days"
                            }) }
                        )
                    }
                }
                if (recMode == "weekly") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(listOf("M", "Tu", "W", "Th", "F", "Sa", "Su").mapIndexed { index, label -> index + 1 to label }) { (day, label) ->
                            FilterChip(
                                selected = day in selectedDays,
                                onClick = {
                                    selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                                },
                                label = { Text(label) }
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
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Surface(
                    onClick = { showNightOptions = !showNightOptions },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sound & snooze", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${snoozeDurations.size} snooze options · ${ringtoneFile.ifBlank { "Built-in melody" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (showNightOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showNightOptions) "Hide night routine options" else "Show night routine options"
                        )
                    }
                }

                AnimatedVisibility(showNightOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider()
                        SectionLabel("Ringtone")
                        OutlinedButton(onClick = { chooseSound = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(ringtoneFile.ifBlank { "Built-in melody" })
                        }
                        if (ringtoneFile.isNotBlank() && ringtoneFile !in availableRingtones) {
                            Text("This file is not in the saved clock library. Check the clock connection or choose another sound.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Text("Snooze durations", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(snoozeDurations.sorted()) { dur ->
                                InputChip(
                                    selected = false, onClick = {},
                                    label = { Text("${dur}m") },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { snoozeDurations = snoozeDurations.toMutableList().also { it.remove(dur) } },
                                            modifier = Modifier.size(32.dp)
                                        ) { Icon(Icons.Default.Close, "Remove ${dur} minutes", Modifier.size(16.dp)) }
                                    }
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newDurText, onValueChange = { if (it.length <= 4) newDurText = it },
                                label = { Text("Add snooze minutes") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val value = newDurText.toIntOrNull()
                                if (value != null && value in 1..1440 && value !in snoozeDurations) {
                                    snoozeDurations = (snoozeDurations + value).toMutableList()
                                    newDurText = ""
                                }
                            }) { Icon(Icons.Default.Add, "Add snooze duration") }
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { requestClose() }) { Text("Cancel") }
                Button(onClick = {
                    val recurrence = when (recMode) {
                        "weekly" -> {
                            if (selectedDays.isEmpty()) { errorMsg = "Select at least one day."; return@Button }
                            Recurrence.Weekly(selectedDays)
                        }
                        "interval" -> {
                            val interval = intervalText.toIntOrNull()
                            if (interval == null || interval !in 1..365) {
                                errorMsg = "Interval must be 1-365 days."
                                return@Button
                            }
                            Recurrence.EveryDays(interval)
                        }
                        else -> Recurrence.Daily
                    }

                    val finalDurations = snoozeDurations.sorted().ifEmpty { AlarmItem.DEFAULT_SNOOZE_DURATIONS }
                    val finalRingtoneFile = ringtoneFile.trim().ifEmpty { null }

                    onSave(
                        title.ifBlank { "Routine" },
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
                        finalRingtoneFile
                    )
                }, modifier = Modifier.weight(1f)) {
                    Text("Save routine")
                }
            }
        }
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
    phoneRequired: Boolean = false,
    deviceRequired: Boolean = false,
    requireOneOutput: Boolean = false,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    onPhoneChange: (Boolean) -> Unit,
    onDeviceChange: (Boolean) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showOutputs by rememberSaveable(title) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(if (!enabled) "Optional" else subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showEnabledToggle) Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                TextButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> onTimeChange(h, m) },
                        hour, minute, DateFormat.is24HourFormat(context)).show()
                }) {
                    Text(String.format(Locale.getDefault(), "%02d:%02d", hour, minute),
                        fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.AccessTime, contentDescription = "Change $title time")
                }
                TextButton(onClick = { showOutputs = !showOutputs }) {
                    Text(listOfNotNull("Phone".takeIf { ringOnPhone }, "Clock".takeIf { ringOnDevice }).joinToString(" + "))
                    Spacer(Modifier.width(8.dp))
                    Icon(if (showOutputs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showOutputs) "Hide sound outputs" else "Change sound outputs")
                }
                AnimatedVisibility(showOutputs) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (requireOneOutput) {
                            listOf("Phone" to (true to false), "Clock" to (false to true),
                                "Both" to (true to true)).forEach { (label, outputs) ->
                                FilterChip(selected = ringOnPhone == outputs.first && ringOnDevice == outputs.second,
                                    onClick = { onPhoneChange(outputs.first); onDeviceChange(outputs.second) },
                                    label = { Text(label) })
                            }
                        } else {
                            SoundOutputControl(Modifier.fillMaxWidth(), "Phone", ringOnPhone, phoneRequired, onPhoneChange)
                            SoundOutputControl(Modifier.fillMaxWidth(), "Clock", ringOnDevice, deviceRequired, onDeviceChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundOutputControl(
    modifier: Modifier,
    label: String,
    checked: Boolean,
    required: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(
                when {
                    required -> "Always on"
                    checked -> "Sounds"
                    else -> "Silent"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (required) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (required) {
            Icon(Icons.Default.CheckCircle, contentDescription = "$label output required",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        } else {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
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
