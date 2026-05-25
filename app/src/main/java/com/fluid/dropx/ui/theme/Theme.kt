package com.fluid.dropx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary      = DarkBrandCharcoal,
    onPrimary    = Color.Black,
    surface      = DarkBrandContainer,
    background   = DarkCanvasBg,
    onBackground = DarkTextPrimary,
    onSurface    = DarkTextPrimary,
    outline      = DarkBorderHairline
)

private val LightColorScheme = lightColorScheme(
    primary      = BrandCharcoal,
    onPrimary    = Color.White,
    surface      = SurfacePrimary,
    background   = BackgroundCanvas,
    onBackground = TextPrimary,
    onSurface    = TextPrimary,
    outline      = BorderHairline
)

@Composable
fun DropXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}