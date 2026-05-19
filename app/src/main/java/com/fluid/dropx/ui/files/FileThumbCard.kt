package com.fluid.dropx.ui.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluid.dropx.core.FileRegistry
import com.fluid.dropx.core.ThumbnailManager
import com.fluid.dropx.model.FileMetadata
import com.fluid.dropx.ui.components.*
import com.fluid.dropx.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An individual asset grid card that handles asynchronous background thumbnail decoding,
 * semantic color badge rendering, and file unlinking callbacks.
 */
@Composable
fun FileThumbCard(
    file        : FileMetadata,
    thumbManager: ThumbnailManager,
    canRemove   : Boolean,
    onRemove    : () -> Unit,
) {
    var bmp by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    val uri = remember(file.id) { FileRegistry.getUri(file.id) }

    // Asynchronous file decoding channel boundary
    LaunchedEffect(key1 = file.id) {
        if (uri != null) {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    thumbManager.getThumbnail(
                        file.name,
                        uri,
                        file.size,
                        file.lastModified
                    )
                }.getOrNull()
            }
            if (bytes != null) {
                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfacePrimary,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // ── VISUAL PREVIEW / THUMBNAIL TRACK AREA ─────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(BackgroundCanvas),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback to high-contrast schematic vector iconography
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(mimeColor(file.mimeType).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mimeIcon(file.mimeType),
                            contentDescription = null,
                            tint = mimeColor(file.mimeType),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // CONTEXTUAL DISMISS × BUTTON
                if (canRemove) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { onRemove() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove File",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // FILE TYPE LABEL BADGE
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = mimeLabel(file.mimeType),
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            // ── ASSET INFORMATION BLOCK ───────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = file.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextPrimary
                )
                Text(
                    text = formatBytes(file.size),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}