package co.id.lintasarta.tagpollinginventory.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = TelecomPrimary,
    onPrimary = NeutralSurface,
    primaryContainer = TelecomPrimaryContainer,
    onPrimaryContainer = TelecomOnPrimaryContainer,
    secondary = TelecomSecondary,
    secondaryContainer = TelecomSecondaryContainer,
    background = NeutralBackground,
    surface = NeutralSurface,
    surfaceVariant = NeutralBackground,
    outline = NeutralBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = LocationBlue,
    primaryContainer = TelecomPrimaryDark,
    background = NeutralBackground,
    surface = NeutralSurface
)

@Composable
fun TagPollingInventoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LightColorScheme else LightColorScheme // Clean high visibility outdoors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
