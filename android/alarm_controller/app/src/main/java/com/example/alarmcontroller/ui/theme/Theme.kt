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
    primary = Color(0xFF84D7BD),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFA1F2D7),
    secondary = Color(0xFFC3C6D6),
    onSecondary = Color(0xFF2D3140),
    secondaryContainer = Color(0xFF444858),
    onSecondaryContainer = Color(0xFFDFE1F2),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF5B1D08),
    tertiaryContainer = Color(0xFF7A2F18),
    onTertiaryContainer = Color(0xFFFFDCCF),
    background = Color(0xFF111A17),
    onBackground = Color(0xFFDCE5DF),
    surface = Color(0xFF111A17),
    onSurface = Color(0xFFDCE5DF),
    surfaceVariant = Color(0xFF414A45),
    onSurfaceVariant = Color(0xFFC1CAC3),
    outline = Color(0xFF8B958E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = Color(0xFF002118),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E2F0),
    onSecondaryContainer = Color(0xFF181B29),
    tertiary = BrandTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCCF),
    onTertiaryContainer = Color(0xFF371B12),
    background = Color(0xFFF5F7F2),
    onBackground = Color(0xFF191D19),
    surface = Color(0xFFF5F7F2),
    onSurface = Color(0xFF191D19),
    surfaceVariant = Color(0xFFE2E5DF),
    onSurfaceVariant = Color(0xFF424943),
    outline = Color(0xFF727B75),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun AlarmControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
