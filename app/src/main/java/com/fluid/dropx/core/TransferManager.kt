package com.fluid.dropx.core

import android.content.Context
import android.net.Uri
import com.fluid.dropx.utils.FileUtil

class TransferManager(private val context: Context) {

    // Wipes the slate clean and starts a fresh sharing session
    fun startTransferSession(selectedUris: List<Uri>) {
        FileRegistry.clear()
        appendTransferSession(selectedUris)
    }

    // Resolves Android system URIs into real metadata (names, sizes, timestamps)
    // and pushes them into the server's memory registry so the web client can see them
    fun appendTransferSession(selectedUris: List<Uri>) {
        selectedUris.forEach { uri ->
            // DocumentFile is used specifically to safely fetch the last modified timestamp from the OS picker
            val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
            val lastMod = doc?.lastModified() ?: 0L
            val (name, size) = FileUtil.getUriMetadata(context, uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            FileRegistry.addFile(uri, name, size, mimeType, lastMod)
        }
    }

    // Wipes the registry, immediately breaking any active download links on the web side
    fun stopTransferSession() {
        FileRegistry.clear()
    }
}