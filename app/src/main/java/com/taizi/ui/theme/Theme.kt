package com.taizi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.os.Build
import androidx.compose.ui.platform.LocalInspectionMode

val BrandAccent = Color(0xFFFF2E63)
val BrandAccentSoft = Color(0xFFFF6B8A)

val DarkColorPalette = darkColorScheme(
    primary = BrandAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A0F1E),
    onPrimaryContainer = BrandAccentSoft,
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF1A1204),
    tertiary = Color(0xFF5B4BFF),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF14141C),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF1D1D28),
    onSurfaceVariant = Color(0xFF9A9AAB),
    outline = Color(0xFF2A2A36),
    outlineVariant = Color(0xFF3A3A48)
)

val LightColorPalette = lightColorScheme(
    primary = BrandAccent,
    onPrimary = Color.White,
    secondary = Color(0xFFFFC857),
    onSecondary = Color.Black,
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF14141C),
    surface = Color.White,
    onSurface = Color(0xFF14141C),
    surfaceVariant = Color(0xFFF1F1F5),
    onSurfaceVariant = Color(0xFF525266),
    outline = Color(0xFFD0D0D8)
)

@Composable
fun TaiziTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorPalette

    var view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(Unit) {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false

            // Font smoothing for crisp text on all platforms
            // Note: View.layerType cannot be reassigned in this context
            // Font smoothing is handled automatically by Compose
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
