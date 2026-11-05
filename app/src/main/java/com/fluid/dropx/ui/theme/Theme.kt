package com.fluid.dropx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every color the UI uses is defined here, so Material never falls back to its default purple tints.
private val DarkColorScheme = darkColorScheme(
    primary            = DarkBrandCharcoal,
    onPrimary          = Color.Black,
    primaryContainer   = DarkSuccessContainer,
    onPrimaryContainer = DarkOnSuccessContainer,
    background         = DarkCanvasBg,
    onBackground       = DarkTextPrimary,
    surface            = DarkBrandContainer,
    onSurface          = DarkTextPrimary,
    surfaceVariant     = DarkSurfaceVariant,
    onSurfaceVariant   = DarkTextSecondary,
    outline            = DarkBorderHairline,
    outlineVariant     = DarkDivider,
    error              = DarkErrorRed,
    onError            = Color.Black,
    errorContainer     = DarkErrorContainer,
    onErrorContainer   = DarkOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary            = BrandCharcoal,
    onPrimary          = Color.White,
    primaryContainer   = SuccessContainer,
    onPrimaryContainer = OnSuccessContainer,
    background         = BackgroundCanvas,
    onBackground       = TextPrimary,
    surface            = SurfacePrimary,
    onSurface          = TextPrimary,
    surfaceVariant     = BrandContainer,
    onSurfaceVariant   = TextSecondary,
    outline            = BorderHairline,
    outlineVariant     = DividerLight,
    error              = ErrorRed,
    onError            = Color.White,
    errorContainer     = ErrorContainerL,
    onErrorContainer   = OnErrorContainerL
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