package de.bananer.zazendroid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Procedural course art: hue rotated from the course id (stable across catalog
 * updates), indigo→teal range. Zero assets, looks intentional.
 */
@Composable
fun rememberCourseBrush(courseId: String): Brush {
    val hue = remember(courseId) { 225f + (courseId.hashCode().and(0x7fffffff) % 60) - 20f }
    return Brush.linearGradient(
        listOf(
            Color.hsl(hue, 0.32f, 0.24f),
            Color.hsl((hue + 35f) % 360f, 0.30f, 0.16f),
        ),
    )
}
