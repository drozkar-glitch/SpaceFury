package cz.mares.spacefury

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpaceDarkScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF002730),
    primaryContainer = Color(0xFF0A2E46),
    onPrimaryContainer = Color(0xFFB8F4FF),
    secondary = Color(0xFFFF4081),
    onSecondary = Color(0xFF3E0018),
    secondaryContainer = Color(0xFF4F112A),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFFFD600),
    background = Color(0xFF070C1A),
    onBackground = Color(0xFFE5EEFF),
    surface = Color(0xFF0F172B),
    onSurface = Color(0xFFE5EEFF),
    surfaceVariant = Color(0xFF182442),
    onSurfaceVariant = Color(0xFFA9B9DA)
)

private val SpaceLightScheme = lightColorScheme(
    primary = Color(0xFF006782),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBE9FF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFFB91656),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF3E0018),
    tertiary = Color(0xFF8C6D00),
    background = Color(0xFFF3F7FD),
    onBackground = Color(0xFF0D172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D172A),
    surfaceVariant = Color(0xFFE1E9F6),
    onSurfaceVariant = Color(0xFF3F4E68)
)

@Composable
fun SpaceFuryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SpaceDarkScheme else SpaceLightScheme,
        content = content
    )
}
