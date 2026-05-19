package com.fluid.dropx.core

import android.net.Uri
import android.util.Log
import com.fluid.dropx.model.FileMetadata
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
* Concurrent HashMap (String->URI)
* */
object FileRegistry {
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

    fun getAllMetadata(): List<FileMetadata> = metadataMap.values.toList()
    fun getMetadata(id: String): FileMetadata? = metadataMap[id]
    fun clear() {
        metadataMap.clear()
        uriMap.clear()
    }
}