package com.fluid.dropx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fluid.dropx.ui.theme.BorderHairline
import com.fluid.dropx.ui.theme.SurfacePrimary
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * A reusable, structural card component that handles consistent scaffolding,
 * borders, and backgrounds across the entire dropX interface.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfacePrimary,
        border = BorderStroke(1.dp, BorderHairline),
        tonalElevation = 0.dp,
        content = content
    )
}