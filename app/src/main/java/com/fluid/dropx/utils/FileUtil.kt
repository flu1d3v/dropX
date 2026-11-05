package com.fluid.dropx.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtil {
    // Reads a file's display name and size from its content:// Uri.
    // Some providers don't report a size in the cursor, so we fall back to the real file descriptor.
    // A returned size <= 0 means "unknown" and the file should not be shared.
    fun getUriMetadata(context: Context, uri: Uri): Pair<String, Long> {
        var name = "unknown_file"
        var size = -1L

        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }

        if (size <= 0L) {
            size = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
        }

        return Pair(name, size)
    }
}