package com.fluid.dropx.ui.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    shareUrl    : String?,          // e.g. http://192.168.1.5:50505/share/abc123  (null when server is stopped)
    onRemove    : () -> Unit,
) {
    val context = LocalContext.current
    var bmp by remember(file.id) { mutableStateOf<Bitmap?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val uri = remember(file.id) { FileRegistry.getUri(file.id) }

    val errorString     = stringResource(R.string.error_unsupported_type)
    val chooserTitle    = stringResource(R.string.chooser_open_with)
    val copiedMsg       = stringResource(R.string.toast_link_copied)
    val needServerMsg   = stringResource(R.string.toast_start_server_first)

    // Side Effect: Triggered automatically whenever the card mounts or the target file.id changes.
    LaunchedEffect(key1 = file.id) {
        if (uri != null) {
            val decodedBitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bytes = thumbManager.getThumbnail(file.name, uri, file.size, file.lastModified)
                    if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
                }.getOrNull()
            }
            bmp = decodedBitmap
        }
    }

    fun openFile() {
        if (uri == null) return
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(viewIntent, chooserTitle))
        }.onFailure {
            Toast.makeText(context, errorString, Toast.LENGTH_SHORT).show()
        }
    }

    // Copies the direct stream/download URL for this single file (paste it into VLC / MX Player etc.)
    fun copyLink() {
        val base = shareUrl
        if (base == null) {
            Toast.makeText(context, needServerMsg, Toast.LENGTH_SHORT).show()
            return
        }
        val link = "$base/file/${file.id}"
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dropX link", link))
        // Android 13+ shows its own "copied" confirmation; older versions need our toast
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    SurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openFile() }
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
                    Image(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
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

                // Quick remove button on the thumbnail (hidden while the server is live)
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
                            contentDescription = stringResource(R.string.cd_remove_file),
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

            // Bottom row: name + size on the left, three-dot menu on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = file.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatBytes(file.size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_file_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_copy_link)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = { menuOpen = false; copyLink() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_open_with)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                            onClick = { menuOpen = false; openFile() }
                        )
                        if (canRemove) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_remove)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; onRemove() }
                            )
                        }
                    }
                }
            }
        }
    }
}