package de.bananer.zazendroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    secondary = Lavender,
    onSecondary = DuskBackground,
    tertiary = Sage,
    background = DuskBackground,
    onBackground = DuskOnSurface,
    surface = DuskSurface,
    onSurface = DuskOnSurface,
    surfaceVariant = DuskSurfaceVariant,
    onSurfaceVariant = DuskMuted,
    error = DuskError,
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = DaySurface,
    secondary = Indigo,
    tertiary = DayAmber,
    background = DayBackground,
    onBackground = DayOnSurface,
    surface = DaySurface,
    onSurface = DayOnSurface,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = DayMuted,
    error = DayError,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun ZazenDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
