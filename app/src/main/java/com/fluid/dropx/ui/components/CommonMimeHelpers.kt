package com.fluid.dropx.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Resolves a uniform Material Design vector icon based on the file's MIME type string.
 */
fun mimeIcon(mime: String): ImageVector = when {
    mime.startsWith("image/")                        -> Icons.Outlined.Image
    mime.startsWith("video/")                        -> Icons.Outlined.Videocam
    mime.startsWith("audio/")                        -> Icons.Outlined.AudioFile
    mime == "application/pdf"                        -> Icons.Outlined.PictureAsPdf
    mime.contains("zip") || mime.contains("archive") -> Icons.Outlined.FolderZip
    else                                             -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/**
 * Resolves a specific color accent token to classify different file types visually.
 */
fun mimeColor(mime: String): Color = when {
    mime.startsWith("image/") -> Color(0xFF4CAF50) // Emerald Green
    mime.startsWith("video/") -> Color(0xFFE91E63) // Deep Pink
    mime.startsWith("audio/") -> Color(0xFF9C27B0) // Royal Purple
    mime == "application/pdf" -> Color(0xFFF44336) // System Red
    else                      -> Color(0xFF607D8B) // Slate Gray
}

/**
 * Normalizes a complex raw system MIME type into a clean, capitalized,
 * reader-friendly text badge string.
 */
fun mimeLabel(mime: String): String = when {
    mime.startsWith("image/") -> "IMAGE"
    mime.startsWith("video/") -> "VIDEO"
    mime.startsWith("audio/") -> "AUDIO"
    mime == "application/pdf" -> "PDF"
    mime.contains("zip")      -> "ZIP"
    else -> mime.substringAfterLast('/').uppercase().take(8)
}

/**
 * Formats a raw filesystem byte count into a clean, human-readable data size string
 * using precise decimal scaling bounds.
 */
fun formatBytes(bytes: Long): String = when {
    bytes < 1_024L         -> "$bytes B"
    bytes < 1_048_576L     -> "${"%.1f".format(bytes / 1_024.0)} KB"
    bytes < 1_073_741_824L -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    else                   -> "${"%.2f".format(bytes / 1_073_741_824.0)} GB"
}