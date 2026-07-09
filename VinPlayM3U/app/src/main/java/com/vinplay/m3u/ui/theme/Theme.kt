package com.vinplay.m3u.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Violet40,
    onPrimary = OnSurfaceHigh,
    primaryContainer = VioletContainerDark,
    secondary = Teal40,
    background = Surface0,
    surface = Surface1,
    surfaceVariant = Surface2,
    onBackground = OnSurfaceHigh,
    onSurface = OnSurfaceHigh,
    onSurfaceVariant = OnSurfaceMuted
)

private val LightColors = lightColorScheme(
    primary = Violet40,
    secondary = Teal40
)

/**
 * App theme. Dark by default; when [dynamicColor] is on and the device supports Material You
 * (Android 12+), it adopts the wallpaper-derived scheme instead of the brand palette.
 */
@Composable
fun VinPlayTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val useDark = darkTheme || isSystemInDarkTheme()
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VinTypography,
        content = content
    )
}
