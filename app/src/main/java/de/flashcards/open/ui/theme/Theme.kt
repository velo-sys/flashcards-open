package de.flashcards.open.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.flashcards.open.data.repo.ThemeMode

/** Colors of the grading buttons and counters (as in the reference prototype). */
@Immutable
data class RatingColors(
    val again: Color,
    val hard: Color,
    val good: Color,
    val easy: Color,
)

val LocalRatingColors = staticCompositionLocalOf {
    RatingColors(Color(0xFFD64545), Color(0xFFD98A1C), Color(0xFF2F9E5B), Color(0xFF3B6FE0))
}

private val Light = lightColorScheme(
    primary = Color(0xFF3B6FE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FB),
    onPrimaryContainer = Color(0xFF0E2A66),
    secondary = Color(0xFF5B6478),
    secondaryContainer = Color(0xFFDCE5FB),
    onSecondaryContainer = Color(0xFF0E2A66),
    surfaceVariant = Color(0xFFE3E7F2),
    background = Color(0xFFF4F4F6),
    surface = Color(0xFFF4F4F6),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECECF0),
    onSurface = Color(0xFF1B1D22),
    onSurfaceVariant = Color(0xFF6B6F7A),
    outline = Color(0xFFD9DAE0),
    outlineVariant = Color(0xFFD9DAE0),
    error = Color(0xFFD64545),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF6F97F2),
    onPrimary = Color(0xFF0B1A3A),
    primaryContainer = Color(0xFF243A6E),
    onPrimaryContainer = Color(0xFFDCE5FB),
    secondary = Color(0xFFA9B1C4),
    secondaryContainer = Color(0xFF2A3A5E),
    onSecondaryContainer = Color(0xFFDCE5FB),
    surfaceVariant = Color(0xFF2A2E37),
    background = Color(0xFF16181D),
    surface = Color(0xFF16181D),
    surfaceContainerLow = Color(0xFF20232A),
    surfaceContainer = Color(0xFF20232A),
    surfaceContainerHigh = Color(0xFF2A2E37),
    onSurface = Color(0xFFE8E9EC),
    onSurfaceVariant = Color(0xFF9A9FAB),
    outline = Color(0xFF353A45),
    outlineVariant = Color(0xFF353A45),
    error = Color(0xFFE05D5D),
)

@Composable
fun FlashcardsTheme(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val ratings = if (dark) {
        RatingColors(Color(0xFFE05D5D), Color(0xFFE0A040), Color(0xFF46B874), Color(0xFF6F97F2))
    } else {
        RatingColors(Color(0xFFD64545), Color(0xFFD98A1C), Color(0xFF2F9E5B), Color(0xFF3B6FE0))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalRatingColors provides ratings) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
