package org.liaocl.careeyes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import org.liaocl.careeyes.data.AppSettings

private val CareEyesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val CareEyesTypography = Typography()

@Composable
fun CareEyesTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.darkMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val scheme = remember(settings.themeId, settings.customSeedArgb, dark) {
        colorSchemeFor(settings.themeId, settings.customSeedArgb, dark)
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = CareEyesShapes,
        typography = CareEyesTypography,
        content = content,
    )
}
