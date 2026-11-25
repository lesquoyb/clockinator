package com.example.alarmcontroller.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** HSV colour plane; value is independent so changing hue preserves intensity.
 * Native sliders expose the same controls to TalkBack and keyboard users.
 * No network writes are made while dragging: Apply sends one RGB command.
 */
@Composable
fun LightColorPicker(rgb: List<Int>?, enabled: Boolean, onApply: (Int, Int, Int) -> Unit) {
    var hue by rememberSaveable { mutableFloatStateOf(30f) }
    var saturation by rememberSaveable { mutableFloatStateOf(0.5f) }
    var intensity by rememberSaveable { mutableFloatStateOf(1f) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(rgb) {
        if (rgb != null && rgb.size == 3) {
            val hsv = FloatArray(3)
            AndroidColor.RGBToHSV(rgb[0], rgb[1], rgb[2], hsv)
            // Black carries no hue; preserve the current colour selection.
            if (hsv[2] > 0f) { hue = hsv[0]; saturation = hsv[1] }
            intensity = hsv[2]
            dirty = false
        }
    }
    val color = Color.hsv(hue, saturation, intensity)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Colour and intensity", style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().height(160.dp)
            .semantics { contentDescription = "Colour palette. Hue and saturation sliders below provide the same controls." }
            .pointerInput(enabled) {
                detectTapGestures { point ->
                    if (enabled) { hue = (point.x / size.width * 359.9f).coerceIn(0f, 359.9f)
                        saturation = (1f - point.y / size.height).coerceIn(0f, 1f); dirty = true }
                }
            }.pointerInput(enabled) {
                detectDragGestures { change, _ ->
                    if (enabled) { change.consume()
                        hue = (change.position.x / size.width * 359.9f).coerceIn(0f, 359.9f)
                        saturation = (1f - change.position.y / size.height).coerceIn(0f, 1f); dirty = true }
                }
            }) {
            drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) }))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White)))
            val marker = Offset(hue / 360f * size.width, (1f - saturation) * size.height)
            drawCircle(Color.Black, 9.dp.toPx(), marker, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, 9.dp.toPx(), marker, style = Stroke(1.5.dp.toPx()))
        }
        Text("Hue", style = MaterialTheme.typography.labelLarge)
        Slider(hue, { hue = it; dirty = true }, valueRange = 0f..359.9f, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Lamp hue" })
        Text("Saturation: ${(saturation * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(saturation, { saturation = it; dirty = true }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Lamp saturation" })
        Text("Intensity: ${(intensity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(intensity, { intensity = it; dirty = true }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Lamp intensity" })
        Button(onClick = {
            onApply((color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt())
            dirty = false
        }, enabled = enabled && dirty, modifier = Modifier.fillMaxWidth()) { Text("Apply colour") }
    }
}
