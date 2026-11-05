package com.fluid.dropx.core

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fluid.dropx.utils.FileUtil

class TransferManager(private val context: Context) {
    // Resolves system URIs into real metadata and adds them to the registry.
    // Files whose size cannot be determined are skipped (they could not be streamed correctly).
    // Returns the number of skipped files so the UI can tell the user.
    fun appendTransferSession(selectedUris: List<Uri>): Int {
        var skipped = 0
        selectedUris.forEach { uri ->
            if (FileRegistry.hasUri(uri)) return@forEach   // already in the list
            val (name, size) = FileUtil.getUriMetadata(context, uri)
            if (size <= 0L) {
                skipped++
                return@forEach
            }
            val lastMod = DocumentFile.fromSingleUri(context, uri)?.lastModified() ?: 0L
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            FileRegistry.addFile(uri, name, size, mimeType, lastMod)
        }
        return skipped
    }

}