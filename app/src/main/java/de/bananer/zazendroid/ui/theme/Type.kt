package de.bananer.zazendroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import de.bananer.zazendroid.R

/** Rounded display face; body and labels stay on the system font. */
val Quicksand = FontFamily(Font(R.font.quicksand))

private val Base = Typography()

val Typography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Quicksand),
    displayMedium = Base.displayMedium.copy(fontFamily = Quicksand),
    displaySmall = Base.displaySmall.copy(fontFamily = Quicksand),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Quicksand),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Quicksand),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Quicksand),
    titleLarge = Base.titleLarge.copy(fontFamily = Quicksand),
    titleMedium = Base.titleMedium.copy(fontFamily = Quicksand),
    titleSmall = Base.titleSmall.copy(fontFamily = Quicksand),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge,
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)
