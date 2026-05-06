package com.fluid.dropx.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/*
* queries Android's ContentResolver to retrieve metadata (display name and size)
* for the given Uri.
*/
object FileUtil {
    fun getUriMetadata(context: Context, uri: Uri) : Pair<String, Long> {
        var name = "unknown_file"
        var size = 0L

        context.contentResolver.query(uri,null,null,null,null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: "unknown"
                size = cursor.getLong(sizeIndex)
            }
        }
        return Pair(name, size)
    }
}