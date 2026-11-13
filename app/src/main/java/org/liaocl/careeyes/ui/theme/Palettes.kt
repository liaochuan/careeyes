package org.liaocl.careeyes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

data class AppPalette(
    val id: String,
    val seed: Color,
)

val BUILT_IN_PALETTES: List<AppPalette> = listOf(
    AppPalette("jade", Color(0xFF0F9D8A)),
    AppPalette("ocean", Color(0xFF2F6FEB)),
    AppPalette("amber", Color(0xFFD9781F)),
    AppPalette("lilac", Color(0xFF7C5CD6)),
    AppPalette("slate", Color(0xFF5B6472)),
)

fun paletteSeed(themeId: String, customSeedArgb: Long?): Color {
    if (themeId == "custom" && customSeedArgb != null) {
        return Color(customSeedArgb.toInt())
    }
    return BUILT_IN_PALETTES.firstOrNull { it.id == themeId }?.seed ?: BUILT_IN_PALETTES.first().seed
}

fun colorSchemeFor(themeId: String, customSeedArgb: Long?, dark: Boolean): ColorScheme {
    val seed = paletteSeed(themeId, customSeedArgb)
    return if (dark) darkScheme(seed) else lightScheme(seed)
}

private fun lightScheme(seed: Color): ColorScheme {
    val primary = seed.adjustValue(if (seed.luminance() > 0.45f) 0.82f else 1f)
    val primaryContainer = seed.mix(Color.White, 0.82f)
    val onPrimaryContainer = seed.mix(Color.Black, 0.55f)
    val onPrimary = if (primary.luminance() > 0.55f) Color(0xFF10201C) else Color.White
    val secondary = seed.mix(Color(0xFF5A6B66), 0.45f)
    val surface = seed.mix(Color.White, 0.975f)
    val surfaceVariant = seed.mix(Color.White, 0.90f)
    val background = seed.mix(Color.White, 0.985f)
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = seed.mix(Color.White, 0.86f),
        onSecondaryContainer = onPrimaryContainer,
        tertiary = seed.rotateHue(38f).mix(Color.White, 0.18f),
        onTertiary = Color.White,
        background = background,
        onBackground = Color(0xFF14201C),
        surface = surface,
        onSurface = Color(0xFF14201C),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFF48544F),
        outline = seed.mix(Color(0xFF6B7671), 0.55f),
        outlineVariant = seed.mix(Color.White, 0.80f),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
    )
}

private fun darkScheme(seed: Color): ColorScheme {
    val primary = if (seed.luminance() < 0.45f) seed.mix(Color.White, 0.32f) else seed
    val primaryContainer = seed.mix(Color(0xFF06110E), 0.55f)
    val onPrimaryContainer = seed.mix(Color.White, 0.80f)
    val onPrimary = if (primary.luminance() > 0.55f) Color(0xFF06201A) else Color(0xFF04110D)
    val background = seed.mix(Color(0xFF080C0B), 0.94f)
    val surface = seed.mix(Color(0xFF0C1210), 0.95f)
    val surfaceVariant = seed.mix(Color(0xFF141C19), 0.88f)
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = seed.mix(Color(0xFF8FA39C), 0.40f),
        onSecondary = Color(0xFF0B1512),
        secondaryContainer = seed.mix(Color(0xFF16221E), 0.72f),
        onSecondaryContainer = seed.mix(Color.White, 0.82f),
        tertiary = seed.rotateHue(38f).mix(Color.White, 0.25f),
        onTertiary = Color(0xFF0B1512),
        background = background,
        onBackground = Color(0xFFE3ECE8),
        surface = surface,
        onSurface = Color(0xFFE3ECE8),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = Color(0xFFB4C2BC),
        outline = seed.mix(Color(0xFF73817B), 0.55f),
        outlineVariant = seed.mix(Color(0xFF24302C), 0.80f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )
}

private fun Color.mix(other: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * f,
        green = green + (other.green - green) * f,
        blue = blue + (other.blue - blue) * f,
        alpha = 1f,
    )
}

private fun Color.adjustValue(factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun Color.rotateHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
