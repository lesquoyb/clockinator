package com.example.alarmcontroller.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFC5BFFF),
    onPrimary = Color(0xFF27224C),
    primaryContainer = Color(0xFF32304E),
    onPrimaryContainer = Color(0xFFE8E3FF),
    secondary = Color(0xFFC3C6D6),
    onSecondary = Color(0xFF2D3140),
    secondaryContainer = Color(0xFF444858),
    onSecondaryContainer = Color(0xFFDFE1F2),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF5B1D08),
    tertiaryContainer = Color(0xFF7A2F18),
    onTertiaryContainer = Color(0xFFFFDCCF),
    background = Color(0xFF151723),
    onBackground = Color(0xFFF1EFF8),
    surface = Color(0xFF151723),
    onSurface = Color(0xFFF1EFF8),
    surfaceVariant = Color(0xFF323544),
    onSurfaceVariant = Color(0xFFB7B8C9),
    surfaceContainer = Color(0xFF222534),
    surfaceContainerLow = Color(0xFF1C1F2C),
    surfaceContainerLowest = Color(0xFF11131D),
    outlineVariant = Color(0xFF444657),
    outline = Color(0xFF9394A6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = Color(0xFF27224C),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E2F0),
    onSecondaryContainer = Color(0xFF181B29),
    tertiary = BrandTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCCF),
    onTertiaryContainer = Color(0xFF371B12),
    background = Color(0xFFF8F6F2),
    onBackground = Color(0xFF242536),
    surface = Color(0xFFF8F6F2),
    onSurface = Color(0xFF242536),
    surfaceVariant = Color(0xFFECE9F3),
    onSurfaceVariant = Color(0xFF626374),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EFF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outlineVariant = Color(0xFFDEDCE7),
    outline = Color(0xFF777888),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun AlarmControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val appearance = rememberAppearance()
    val effectiveDarkTheme = when (appearance.mode) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }
    val colorScheme = when {
        (dynamicColor || appearance.dynamicColor) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
