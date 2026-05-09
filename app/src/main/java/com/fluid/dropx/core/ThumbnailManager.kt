package com.fluid.dropx.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.collection.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ThumbnailManager(private val context: Context) {
    private val memoryCache = object : LruCache<String, ByteArray>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    class GenerationState {
        val mutex = Mutex()
        val refCount = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val activeGenerations = ConcurrentHashMap<String, GenerationState>()
    private val globalGenLimit = kotlinx.coroutines.sync.Semaphore(3)

    private val currentDiskUsage = java.util.concurrent.atomic.AtomicLong(0L)
    private val diskQuota = 50 * 1024 * 1024L

    init {
        val thumbDir = File(context.cacheDir, "thumbs")
        if (thumbDir.exists()) {
            currentDiskUsage.set(thumbDir.listFiles()?.sumOf { it.length() } ?: 0L)
        }
    }

    suspend fun getThumbnail(name: String, uri: android.net.Uri, size: Long, lastModified: Long): ByteArray? {
        val hash = CacheKeyGenerator.generate(name,size,lastModified)

        memoryCache.get(hash)?.let { return it }

        val state = activeGenerations.compute(hash) { _, existing ->
            (existing ?: GenerationState()).apply { refCount.incrementAndGet() }
        }!!

        return try {
            state.mutex.withLock {
                memoryCache.get(hash)?.let { return@withLock it }

                val cacheFile = File(context.cacheDir, "thumbs/$hash.thumb")
                val failMarker = File(context.cacheDir, "thumbs/$hash.failed")

                if (failMarker.exists()) return@withLock null
                if (cacheFile.exists()) {
                    val bytes = cacheFile.readBytes()
                    memoryCache.put(hash, bytes)
                    return@withLock bytes
                }

                globalGenLimit.withPermit {
                    generateAndStore(hash, uri, cacheFile, failMarker)
                }
            }
        } finally {
            activeGenerations.compute(hash) { _, existing ->
                if (existing != null && existing.refCount.decrementAndGet() == 0) {
                    null
                } else {
                    existing
                }
            }
        }
    }

    private fun generateAndStore(hash: String, uri: android.net.Uri, cacheFile: File, failMarker: File): ByteArray? {
        return try {
            val bitmap = context.contentResolver.loadThumbnail(uri, Size(256, 256), null)

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()


            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(bytes)

            val newSize = currentDiskUsage.addAndGet(bytes.size.toLong())
            if (newSize > diskQuota) {
                pruneDiskCache()
            }

            memoryCache.put(hash, bytes)
            bytes
        } catch (e: Exception) {
            failMarker.parentFile?.mkdirs()
            failMarker.createNewFile()
            null
        }
    }

    private fun pruneDiskCache() {
        val thumbDir = File(context.cacheDir, "thumbs")
        val files = thumbDir.listFiles()?.sortedBy { it.lastModified() } ?: return

        val targetSize = (diskQuota * 0.8).toLong()
        var deletedBytes = 0L

        for (file in files) {
            if (currentDiskUsage.get() - deletedBytes <= targetSize) break

            val size = file.length()
            if (file.delete()) {
                deletedBytes += size
            }
        }
        currentDiskUsage.addAndGet(-deletedBytes)
    }
}
