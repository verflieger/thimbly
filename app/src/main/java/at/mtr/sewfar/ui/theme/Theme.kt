package at.mtr.sewfar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// SewFar's own scheme (SPEC 1): petrol like indigo-dyed cotton, terracotta thread, linen surfaces.

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6474),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9EAFB),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF8E4E33),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCE),
    onSecondaryContainer = Color(0xFF370E00),
    tertiary = Color(0xFF5E6135),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E6AE),
    onTertiaryContainer = Color(0xFF1B1D00),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1E1B17),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1E1B17),
    surfaceVariant = Color(0xFFEDE4D8),
    onSurfaceVariant = Color(0xFF4D463C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1EA),
    surfaceContainer = Color(0xFFF1EBE3),
    surfaceContainerHigh = Color(0xFFEBE5DD),
    surfaceContainerHighest = Color(0xFFE5DFD7),
    outline = Color(0xFF7F766B),
    outlineVariant = Color(0xFFD0C5B8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCEDF),
    onPrimary = Color(0xFF003543),
    primaryContainer = Color(0xFF0F4D5C),
    onPrimaryContainer = Color(0xFFB9EAFB),
    secondary = Color(0xFFFFB598),
    onSecondary = Color(0xFF552009),
    secondaryContainer = Color(0xFF71371E),
    onSecondaryContainer = Color(0xFFFFDBCE),
    tertiary = Color(0xFFC7CA94),
    onTertiary = Color(0xFF30330B),
    tertiaryContainer = Color(0xFF46491F),
    onTertiaryContainer = Color(0xFFE3E6AE),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFE9E1D9),
    surface = Color(0xFF16130F),
    onSurface = Color(0xFFE9E1D9),
    surfaceVariant = Color(0xFF4D463C),
    onSurfaceVariant = Color(0xFFD0C5B8),
    surfaceContainerLowest = Color(0xFF110E0A),
    surfaceContainerLow = Color(0xFF1E1B17),
    surfaceContainer = Color(0xFF231F1B),
    surfaceContainerHigh = Color(0xFF2D2925),
    surfaceContainerHighest = Color(0xFF38342F),
    outline = Color(0xFF999084),
    outlineVariant = Color(0xFF4D463C),
)

/** Light/dark follows the system; [dynamicColor] switches to the wallpaper-based Material You scheme. */
@Composable
fun SewFarTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        dynamicColor && dark -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor -> dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
