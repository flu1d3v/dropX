package com.fluid.dropx.core

import android.net.Uri
import com.fluid.dropx.model.FileMetadata
import java.util.UUID

// In-memory registry that maps selected files to random UUIDs.
// The network only ever sees the random id, never the real file path/URI.
// Files keep the order in which they were added.
object FileRegistry {
    private val lock = Any()
    private val metadataMap = LinkedHashMap<String, FileMetadata>()
    private val uriMap = HashMap<String, Uri>()

    fun addFile(uri: Uri, name: String, size: Long, mime: String, lastModified: Long): String {
        val id = UUID.randomUUID().toString()
        val metadata = FileMetadata(
            id = id,
            name = name,
            size = size,
            mimeType = mime,
            lastModified = lastModified
        )
        synchronized(lock) {
            metadataMap[id] = metadata
            uriMap[id] = uri
        }
        return id
    }

    fun removeFile(id: String) {
        synchronized(lock) {
            metadataMap.remove(id)
            uriMap.remove(id)
        }
    }

    fun hasUri(uri: Uri): Boolean = synchronized(lock) { uriMap.containsValue(uri) }
    fun getUri(id: String): Uri? = synchronized(lock) { uriMap[id] }

    fun getMetadata(id: String): FileMetadata? = synchronized(lock) { metadataMap[id] }

    fun getAllMetadata(): List<FileMetadata> = synchronized(lock) { metadataMap.values.toList() }

    fun clear() {
        synchronized(lock) {
            metadataMap.clear()
            uriMap.clear()
        }
    }
}