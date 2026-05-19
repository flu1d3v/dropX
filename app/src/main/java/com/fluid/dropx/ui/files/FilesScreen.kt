package com.fluid.dropx.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluid.dropx.core.ThumbnailManager
import com.fluid.dropx.model.FileMetadata
import com.fluid.dropx.ui.theme.*

@Composable
fun FilesScreen(
    files        : List<FileMetadata>,
    serverRunning: Boolean,
    onPickFiles  : () -> Unit,
    onRemoveFile : (String) -> Unit,
) {
    val context = LocalContext.current
    // Instantiated once and remembered to protect the composition scope from garbage collection thrashing
    val thumbManager = remember { ThumbnailManager(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundCanvas)
            .statusBarsPadding()
    ) {
        // ── TOP CONTROLS HEADER ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Shared Files",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onPickFiles,
                enabled = !serverRunning,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = BrandContainer,
                    contentColor = BrandCharcoal,
                    disabledContainerColor = BackgroundCanvas,
                    disabledContentColor = TextMuted
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Files", fontSize = 13.sp)
            }
        }

        // ── SUB-HEADER METADATA STRIP ────────────────────────────────────────
        if (files.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${files.size} file${if (files.size != 1) "s" else ""}", fontSize = 12.sp, color = TextSecondary)
                if (serverRunning) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusSuccessSurf)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Live", fontSize = 11.sp, color = StatusSuccess, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Stop server to remove files", fontSize = 11.sp, color = TextMuted)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // ── WORKSPACE CONFIGURATION SWITCH ───────────────────────────────────
        if (files.isEmpty()) {
            EmptyFilesState(onPickFiles)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(count = 2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
                verticalArrangement = Arrangement.spacedBy(space = 10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files, key = { it.id }) { file ->
                    FileThumbCard(
                        file = file,
                        thumbManager = thumbManager,
                        canRemove = !serverRunning,
                        onRemove = { onRemoveFile(file.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFilesState(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(BrandContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.FolderOpen, null, tint = BrandCharcoal, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No files selected", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add files to share them over the\nlocal network — no app install needed.",
            fontSize = 13.sp, color = TextMuted, textAlign = TextAlign.Center, lineHeight = 19.sp
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onPick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandCharcoal)
        ) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Select Files")
        }
    }
}