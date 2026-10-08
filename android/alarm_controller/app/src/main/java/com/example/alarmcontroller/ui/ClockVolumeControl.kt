package com.example.alarmcontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.alarmcontroller.ui.theme.AlarmControllerTheme

@Composable
fun ClockVolumeControl(level: Int, confirmed: Int?, connected: Boolean, onSelect: (Int) -> Unit,
    ringtoneFiles: List<String> = emptyList(), isTesting: Boolean = false,
    onTest: (String?) -> Unit = {}, onStop: () -> Unit = {}) {
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val testFile = selectedFile?.takeIf { it in ringtoneFiles }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Level $level of 5", style = MaterialTheme.typography.bodyLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((1..5).toList()) { value ->
                FilterChip(selected = level == value, onClick = { onSelect(value) },
                    label = { Text(value.toString()) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Clock ringtone volume, level $value of 5"
                    })
            }
        }
        Text(when {
            !connected -> "Saved on this phone. Applies when the clock reconnects."
            confirmed == level -> "Confirmed by the clock."
            else -> "Waiting for confirmation. Tap a level again to retry."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("1 is quiet · 5 is maximum. No mute. Phone alarms use the phone's volume.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Test ringtone", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { menuOpen = true }, enabled = !isTesting) {
                Text(testFile ?: "Built-in melody")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Built-in melody") }, onClick = {
                    selectedFile = null; menuOpen = false
                })
                ringtoneFiles.forEach { filename ->
                    DropdownMenuItem(text = { Text(filename) }, onClick = {
                        selectedFile = filename; menuOpen = false
                    })
                }
            }
        }
        Button(onClick = { if (isTesting) onStop() else onTest(testFile) }, enabled = connected) {
            Text(if (isTesting) "Stop test" else "Play on clock · 5 seconds")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClockVolumePreview() {
    AlarmControllerTheme { ClockVolumeControl(3, 3, true, onSelect = {}) }
}
