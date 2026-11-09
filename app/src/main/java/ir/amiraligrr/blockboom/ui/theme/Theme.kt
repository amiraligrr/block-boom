package ir.amiraligrr.blockboom.ui.theme

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ir.amiraligrr.blockboom.data.JoyColors

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * App theme.
 *
 * Dark mode used to be hardcoded off: [provideJoyThemeColors] was always
 * called with `false` and the scheme was always `lightColorScheme`, so a player
 * with system dark mode on got a blinding white screen and — because
 * values-v27/themes.xml pinned `windowLightStatusBar=false` — white status bar
 * icons on a white background, i.e. an invisible status bar.
 */
@Composable
fun BlockBoomTheme(
    /** Overrides the system setting; null follows the system. */
    darkMode: Boolean? = null,
    content: @Composable () -> Unit
) {
    val isDark = darkMode ?: isSystemInDarkTheme()
    val light = JoyColors.Light
    val dark = JoyDarkColors

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = dark.primary,
            onPrimary = dark.onPrimary,
            primaryContainer = dark.surfaceVariant,
            onPrimaryContainer = dark.onSurface,
            secondary = dark.secondary,
            onSecondary = dark.onPrimary,
            secondaryContainer = dark.surfaceVariant,
            onSecondaryContainer = dark.onSurface,
            tertiary = dark.accent,
            onTertiary = dark.onPrimary,
            tertiaryContainer = dark.surfaceVariant,
            background = dark.backgroundTop,
            onBackground = dark.textPrimary,
            surface = dark.surface,
            onSurface = dark.onSurface,
            surfaceVariant = dark.surfaceVariant,
            onSurfaceVariant = dark.textMuted,
            surfaceBright = dark.cardBackground,
            surfaceDim = dark.backgroundMid,
            error = dark.error,
            onError = Color.White,
            outline = dark.cardBorder,
            outlineVariant = dark.divider,
            scrim = Color.Black.copy(alpha = 0.55f)
        )
    } else {
        lightColorScheme(
            primary = light.primary,
            onPrimary = light.onPrimary,
            primaryContainer = light.surfaceVariant,
            onPrimaryContainer = light.onSurface,
            secondary = light.secondary,
            onSecondary = light.onPrimary,
            secondaryContainer = light.surfaceVariant,
            onSecondaryContainer = light.onSurface,
            tertiary = light.accent,
            onTertiary = light.onPrimary,
            tertiaryContainer = light.surfaceVariant,
            background = light.backgroundTop,
            onBackground = light.textPrimary,
            surface = light.surface,
            onSurface = light.onSurface,
            surfaceVariant = light.surfaceVariant,
            onSurfaceVariant = light.textMuted,
            surfaceBright = light.cardBackground,
            surfaceDim = light.backgroundMid,
            error = light.error,
            onError = light.onPrimary,
            outline = light.cardBorder,
            outlineVariant = light.divider,
            scrim = Color.Black.copy(alpha = 0.3f)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Match the system bar icon colour to the theme, otherwise the
            // status bar is invisible in one mode or the other.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = {
            provideJoyThemeColors(isDark, content)
        }
    )
}

@Composable
fun JoyBackground(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val colors = MaterialTheme.colorScheme
    modifier.background(colors.surface)
}
