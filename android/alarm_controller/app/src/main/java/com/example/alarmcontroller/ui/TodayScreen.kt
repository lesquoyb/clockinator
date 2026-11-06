package com.example.alarmcontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.RingtoneType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun RingtoneType.displayLabel(): String = when (this) {
    RingtoneType.LOCK_PHONE -> "Focus"
    RingtoneType.BEDTIME -> "Bedtime"
    RingtoneType.WAKEUP -> "Wake-up"
}

@Composable
fun TodayScreen(alarms: List<AlarmItem>, connected: Boolean, pendingSync: Boolean,
    onCreate: () -> Unit, onOpenClock: () -> Unit, onEdit: (AlarmItem) -> Unit,
    onSkip: (UpcomingEvent) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }
    val events = upcomingEvents(alarms, now)
    val context = LocalContext.current
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    val dateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (events.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No upcoming routine", style = MaterialTheme.typography.headlineSmall)
                        Text(if (alarms.isEmpty()) "Create a routine to set your Focus, Bedtime and Wake-up times."
                            else "Your routines are paused. Open Routines to turn one on.")
                        Button(onClick = onCreate) { Text("Create a routine") }
                    }
                }
            }
        } else {
            val next = events.first()
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Next routine event", style = MaterialTheme.typography.labelLarge)
                        Text(next.type.displayLabel(), style = MaterialTheme.typography.titleLarge)
                        Text(timeFormat.format(Date(next.timeMillis)), fontSize = 52.sp, lineHeight = 60.sp)
                        Text("${dateFormat.format(Date(next.timeMillis))} · ${next.alarm.title}")
                        val slot = next.alarm.slot(next.type)
                        Text(listOfNotNull("Phone".takeIf { slot.ringOnPhone },
                            "Clock".takeIf { slot.ringOnDevice }).joinToString(" + "),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onEdit(next.alarm) }) { Text("Edit routine") }
                        // A skip already in effect must be undone from its routine;
                        // do not offer a misleading second skip for the later cycle.
                        if (!next.alarm.isDismissed(next.type)) {
                            TextButton(onClick = { onSkip(next) }) { Text("Skip this ${next.type.displayLabel().lowercase()} once") }
                        }
                    }
                }
            }
        }
        item {
            OutlinedCard(onClick = onOpenClock, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(if (connected) "Clock available" else "Clock disconnected", style = MaterialTheme.typography.titleSmall)
                    Text(if (pendingSync) "Changes waiting to be sent to the clock"
                        else "Changes saved on this phone", style = MaterialTheme.typography.bodyMedium)
                    Text("Manage clock →", color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (events.size > 1) {
            item { Text("Coming up", style = MaterialTheme.typography.titleMedium) }
            items(events.drop(1).take(5), key = { "${it.alarm.id}:${it.type}" }) { event ->
                Surface(onClick = { onEdit(event.alarm) }, shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("${event.type.displayLabel()} · ${timeFormat.format(Date(event.timeMillis))}",
                            style = MaterialTheme.typography.titleMedium)
                        Text("${dateFormat.format(Date(event.timeMillis))} · ${event.alarm.title}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text("Routine schedule. An active snooze may ring sooner.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
