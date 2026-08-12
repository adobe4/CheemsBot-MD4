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
import com.vinplay.m3u.BuildConfig

// Accent colors come from the per-flavor BrandPalette (max = violet/teal, red = red).
private val DarkColors = darkColorScheme(
    primary = BrandPalette.primary,
    onPrimary = OnSurfaceHigh,
    primaryContainer = BrandPalette.primaryContainer,
    secondary = BrandPalette.secondary,
    background = Surface0,
    surface = Surface1,
    surfaceVariant = Surface2,
    onBackground = OnSurfaceHigh,
    onSurface = OnSurfaceHigh,
    onSurfaceVariant = OnSurfaceMuted
)

private val LightColors = lightColorScheme(
    primary = BrandPalette.primary,
    secondary = BrandPalette.secondary
)

/**
 * App theme. Dark by default; when [dynamicColor] is on and the device supports Material You
 * (Android 12+), it adopts the wallpaper-derived scheme instead of the brand palette. The default
 * comes from the product flavor: the "red" clone disables it so its red palette always shows.
 */
@Composable
fun VinPlayTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = BuildConfig.DYNAMIC_COLOR,
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
