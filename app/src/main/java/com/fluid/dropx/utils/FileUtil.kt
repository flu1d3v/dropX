package com.fluid.dropx.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtil {
    fun getUriMetadata(context: Context, uri: Uri) : Pair<String, Long> {
        var name = "unknown_file"
        var size = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)

                    name = cursor.getString(nameIndex) ?: "unknown"
                    size = cursor.getLong(sizeIndex)
                }
            }
        }.onFailure {
        }

        return Pair(name, size)
    }
}