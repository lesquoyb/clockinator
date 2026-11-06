package com.example.alarmcontroller.ui.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class Appearance(val mode: String, val dynamicColor: Boolean)

@Composable
fun rememberAppearance(): Appearance {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("appearance", 0) }
    fun read() = Appearance(preferences.getString("mode", "system") ?: "system",
        preferences.getBoolean("dynamicColor", false))
    var appearance by remember(preferences) { mutableStateOf(read()) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> appearance = read() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return appearance
}

@Composable
fun AppearanceSettings() {
    val appearance = rememberAppearance()
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("appearance", 0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
            Surface(onClick = { preferences.edit().putString("mode", mode).apply() },
                shape = MaterialTheme.shapes.medium,
                color = if (appearance.mode == mode) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = appearance.mode == mode, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Use phone colours", Modifier.weight(1f))
                Switch(appearance.dynamicColor,
                    onCheckedChange = { preferences.edit().putBoolean("dynamicColor", it).apply() })
            }
        }
    }
}
