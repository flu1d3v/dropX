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
    // Memory Cache: Grabs up to 1/16th of the app's available JVM memory pool to hold active images in RAM.
    private val memoryCache = object : LruCache<String, ByteArray>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    // Tracks simultaneous incoming network requests for the exact same thumbnail image.
    class GenerationState {
        val mutex = Mutex()
        val refCount = java.util.concurrent.atomic.AtomicInteger(0)
    }

    private val activeGenerations = ConcurrentHashMap<String, GenerationState>()

    // Throttler: Forces a hard limit of maximum 3 parallel thumbnail generations.
    // Prevents the phone's CPU from getting flooded if a web client requests hundreds of grid items at once.
    private val globalGenLimit = kotlinx.coroutines.sync.Semaphore(3)

    // Storage tracking mechanics for the disk layer
    private val currentDiskUsage = java.util.concurrent.atomic.AtomicLong(0L)
    private val diskQuota = 50 * 1024 * 1024L // Max 50 MB disk space allowed for cached images

    private val diskMutex = Mutex()

    init {
        // Bootstrap: Calculate current disk cache weight on system startup
        val thumbDir = File(context.cacheDir, "thumbs")
        if (thumbDir.exists()) {
            currentDiskUsage.set(thumbDir.listFiles()?.filter { it.name.endsWith(".thumb") }?.sumOf { it.length() } ?: 0L)
        }
    }

    suspend fun getThumbnail(name: String, uri: android.net.Uri, size: Long, lastModified: Long): ByteArray? {
        val hash = CacheKeyGenerator.generate(name, size, lastModified)

        // 1. Fast path: check memory cache first
        memoryCache.get(hash)?.let { return it }

        // 2. Single-Flight Logic: If multiple web threads request the same file, increment refcount
        // so only the first request actually generates it, while the others wait in line on the mutex.
        val state = activeGenerations.compute(hash) { _, existing ->
            (existing ?: GenerationState()).apply { refCount.incrementAndGet() }
        }!!

        return try {
            // Double-check memory cache right after acquiring lock in case another thread just filled it
            state.mutex.withLock {
                memoryCache.get(hash)?.let { return@withLock it }

                val cacheFile = File(context.cacheDir, "thumbs/$hash.thumb")

                // 3. Check persistent local disk cache
                if (cacheFile.exists()) {
                    val bytes = diskMutex.withLock { cacheFile.readBytes() }
                    memoryCache.put(hash, bytes)
                    return@withLock bytes
                }

                // 4. Cache Miss: Wait for a slot in the semaphore and generate a fresh thumbnail
                globalGenLimit.withPermit {
                    generateAndStore(hash, uri, cacheFile)
                }
            }
        } finally {
            // Clean up tracking map references once all waiting requests finish reading the item
            activeGenerations.compute(hash) { _, existing ->
                if (existing != null && existing.refCount.decrementAndGet() == 0) {
                    null
                } else {
                    existing
                }
            }
        }
    }

    private suspend fun generateAndStore(hash: String, uri: android.net.Uri, cacheFile: File): ByteArray? {
        return try {
            // Calls Android OS platform native thumbnail extractor (handles videos, docs, and images safely)
            val bitmap = context.contentResolver.loadThumbnail(uri, Size(256, 256), null)

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()

            diskMutex.withLock {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(bytes)

                val newSize = currentDiskUsage.addAndGet(bytes.size.toLong())
                if (newSize > diskQuota) {
                    pruneDiskCache()
                }
            }

            memoryCache.put(hash, bytes)
            bytes
        } catch (e: Exception) {
            null
        }
    }

    // LRU Pruning logic: If we overshoot the 50MB budget, wipe oldest files until we are down to 80% capacity (40MB).
    private fun pruneDiskCache() {
        val thumbDir = File(context.cacheDir, "thumbs")
        val files = thumbDir.listFiles()?.filter { it.name.endsWith(".thumb") }?.sortedBy { it.lastModified() } ?: return

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