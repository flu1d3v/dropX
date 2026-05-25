package com.fluid.dropx.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtil {
    // Queries the Android Content Resolver system to pull metadata about a chosen Uri.
    // This is required because Scoped Storage won't give you direct java.io.File access paths.
    fun getUriMetadata(context: Context, uri: Uri) : Pair<String, Long> {
        var name = "unknown_file"
        var size = 0L

        runCatching {
            // Opens a virtual data cursor pointing directly to the Android media system registry rows
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)

                    name = cursor.getString(nameIndex) ?: "unknown"
                    size = cursor.getLong(sizeIndex)
                }
            }
        }.onFailure {
            // Fallback default variables will be returned if the file picker session token lacks query rights
        }

        return Pair(name, size)
    }
}