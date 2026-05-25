package com.fluid.dropx.ui.files

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluid.dropx.R
import com.fluid.dropx.core.FileRegistry
import com.fluid.dropx.core.ThumbnailManager
import com.fluid.dropx.model.FileMetadata
import com.fluid.dropx.ui.components.*
import kotlinx.coroutines.withContext

@Composable
fun FileThumbCard(
    file        : FileMetadata,
    thumbManager: ThumbnailManager,
    canRemove   : Boolean,
    onRemove    : () -> Unit,
) {
    val context = LocalContext.current
    var bmp by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    val uri = remember(file.id) { FileRegistry.getUri(file.id) }
    val errorString = stringResource(R.string.error_unsupported_type)

    // Side Effect: Triggered automatically whenever the card mounts or the target file.id changes.
    LaunchedEffect(key1 = file.id) {
        if (uri != null) {
            // Dispatches to the IO thread pool so processing binary bitmap arrays doesn't drop UI frame rates
            val decodedBitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bytes = thumbManager.getThumbnail(
                        file.name,
                        uri,
                        file.size,
                        file.lastModified
                    )
                    if (bytes != null) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                }.getOrNull()
            }
            bmp = decodedBitmap // Pushes the bitmap back to the main thread to force layout refresh
        }
    }

    SurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (uri != null) {
                        // Broadcast Intent: Invokes the native Android system file viewer matching the target MIME type
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, file.mimeType)
                            // Crucial flag: grants temporary workspace access rights to the external viewing app
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        runCatching {
                            val chooserIntent = Intent.createChooser(viewIntent, "Open file with...")
                            context.startActivity(chooserIntent)
                        }.onFailure {
                            Toast.makeText(context, errorString, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        ) {
            // Top Preview Container Box Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    // Render image thumbnail if the bitmap decoder extraction succeeded
                    Image(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback state placeholder: Displays an explicit colored vector asset block matching the MIME class
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

                // Delete Floating Action Layer: hidden automatically if the server engine is active
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
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Bottom Left Raw Type Badge Flag (e.g., "IMAGE", "VIDEO", "PDF")
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

            // Bottom Description metadata text labels
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = file.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis, // Clips text overflow with cleanly trailing trailing dots
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatBytes(file.size),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}