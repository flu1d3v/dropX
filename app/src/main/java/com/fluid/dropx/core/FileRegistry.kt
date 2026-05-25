package com.fluid.dropx.core

import android.net.Uri
import com.fluid.dropx.model.FileMetadata
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// In-memory registry to map selected files to randomly generated UUIDs.
// This completely hides real file paths/URIs from the network. The client only sees the random ID.
object FileRegistry {
    // ConcurrentHashMaps ensure thread safety when multiple clients request metadata or stream files simultaneously
    private val uriMap = ConcurrentHashMap<String, Uri>()
    private val metadataMap = ConcurrentHashMap<String, FileMetadata>()

    fun addFile(uri: Uri, name: String, size: Long, mime: String, lastModified: Long): String {
        val secureId = UUID.randomUUID().toString()

        val metadata = FileMetadata(
            id = secureId,
            name = name,
            size = size,
            mimeType = mime,
            lastModified = lastModified
        )

        metadataMap[secureId]=metadata
        uriMap[secureId]=uri

        return secureId
    }

    fun removeFile(id: String) {
        metadataMap.remove(id)
        uriMap.remove(id)
    }

    fun getUri(id: String): Uri? = uriMap[id]

    // Using synchronized wrapper here to prevent ConcurrentModificationException when converting the map values to a flat list
    fun getAllMetadata(): List<FileMetadata> {
        synchronized(metadataMap) {
            return metadataMap.values.toList()
        }
    }
    fun getMetadata(id: String): FileMetadata? = metadataMap[id]
    fun clear() {
        metadataMap.clear()
        uriMap.clear()
    }
}