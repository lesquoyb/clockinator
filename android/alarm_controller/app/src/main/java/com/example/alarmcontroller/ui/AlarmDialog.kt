package com.example.alarmcontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.Recurrence

@Composable
fun AlarmDialog(
    initialAlarm: AlarmItem? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, recurrence: Recurrence) -> Unit
) {
    var title by remember { mutableStateOf(initialAlarm?.title ?: "Réveil") }
    var startHourText by remember { mutableStateOf((initialAlarm?.startHour ?: 7).toString()) }
    var startMinuteText by remember { mutableStateOf(String.format("%02d", initialAlarm?.startMinute ?: 0)) }
    var endHourText by remember { mutableStateOf((initialAlarm?.endHour ?: 8).toString()) }
    var endMinuteText by remember { mutableStateOf(String.format("%02d", initialAlarm?.endMinute ?: 0)) }
    var recurrenceMode by remember {
        mutableStateOf(
            when (initialAlarm?.recurrence) {
                is Recurrence.Weekly -> "weekly"
                is Recurrence.EveryDays -> "interval"
                else -> "daily"
            }
        )
    }
    var selectedDays by remember {
        mutableStateOf((initialAlarm?.recurrence as? Recurrence.Weekly)?.days ?: setOf(1, 2, 3, 4, 5))
    }
    var intervalDaysText by remember {
        mutableStateOf(((initialAlarm?.recurrence as? Recurrence.EveryDays)?.interval ?: 2).toString())
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialAlarm == null) "Nouvelle alarme" else "Modifier l'alarme",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nom de l'alarme") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Horaire de DÉPART :",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startHourText,
                        onValueChange = { if (it.length <= 2) startHourText = it },
                        label = { Text("Heure (0-23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    OutlinedTextField(
                        value = startMinuteText,
                        onValueChange = { if (it.length <= 2) startMinuteText = it },
                        label = { Text("Min (0-59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Horaire de FIN :",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = endHourText,
                        onValueChange = { if (it.length <= 2) endHourText = it },
                        label = { Text("Heure (0-23)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    OutlinedTextField(
                        value = endMinuteText,
                        onValueChange = { if (it.length <= 2) endMinuteText = it },
                        label = { Text("Min (0-59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Récurrence", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = recurrenceMode == "daily", onClick = { recurrenceMode = "daily" }, label = { Text("Chaque jour") })
                    FilterChip(selected = recurrenceMode == "weekly", onClick = { recurrenceMode = "weekly" }, label = { Text("Jours choisis") })
                    FilterChip(selected = recurrenceMode == "interval", onClick = { recurrenceMode = "interval" }, label = { Text("Tous les N jours") })
                }
                if (recurrenceMode == "weekly") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { index, label ->
                            FilterChip(
                                selected = index + 1 in selectedDays,
                                onClick = {
                                    val day = index + 1
                                    selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                                },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (recurrenceMode == "interval") {
                    OutlinedTextField(
                        value = intervalDaysText,
                        onValueChange = { if (it.length <= 3) intervalDaysText = it },
                        label = { Text("Intervalle en jours (1-365)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sH = startHourText.toIntOrNull()
                    val sM = startMinuteText.toIntOrNull()
                    val eH = endHourText.toIntOrNull()
                    val eM = endMinuteText.toIntOrNull()

                    if (sH == null || sH !in 0..23 || sM == null || sM !in 0..59) {
                        errorMessage = "L'horaire de départ est invalide (0-23h et 0-59min)."
                        return@Button
                    }
                    if (eH == null || eH !in 0..23 || eM == null || eM !in 0..59) {
                        errorMessage = "L'horaire de fin est invalide (0-23h et 0-59min)."
                        return@Button
                    }

                    val recurrence = when (recurrenceMode) {
                        "weekly" -> if (selectedDays.isEmpty()) {
                            errorMessage = "Sélectionnez au moins un jour."
                            return@Button
                        } else Recurrence.Weekly(selectedDays)
                        "interval" -> {
                            val interval = intervalDaysText.toIntOrNull()
                            if (interval == null || interval !in 1..365) {
                                errorMessage = "L'intervalle doit être compris entre 1 et 365 jours."
                                return@Button
                            }
                            Recurrence.EveryDays(interval)
                        }
                        else -> Recurrence.Daily
                    }
                    onSave(title.ifBlank { "Alarme" }, sH, sM, eH, eM, recurrence)
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
