package com.fluid.dropx.core

import android.content.Context
import android.net.Uri
import com.fluid.dropx.utils.FileUtil

class TransferManager(private val context: Context) {


    fun startTransferSession(selectedUris: List<Uri>) {
        FileRegistry.clear()
        appendTransferSession(selectedUris)
    }


    fun appendTransferSession(selectedUris: List<Uri>) {
        selectedUris.forEach { uri ->
            val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
            val lastMod = doc?.lastModified() ?: 0L
            val (name, size) = FileUtil.getUriMetadata(context, uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            FileRegistry.addFile(uri, name, size, mimeType, lastMod)
        }
    }


    fun stopTransferSession() {
        FileRegistry.clear()
    }
}