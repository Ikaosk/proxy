package com.proxyvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    secondaryContainer = BlueSurfaceLight,
    surfaceVariant = BlueSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = BluePrimaryDark,
    secondaryContainer = BlueSurfaceDark,
    surfaceVariant = BlueSurfaceDark,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
