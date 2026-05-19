package com.fluid.dropx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun DropXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary      = BrandCharcoal,
            onPrimary    = Color.White,
            surface      = SurfacePrimary,
            background   = BackgroundCanvas,
            onBackground = TextPrimary,
            onSurface     = TextPrimary,
            outline      = BorderHairline,
        ),
        content = content
    )
}
