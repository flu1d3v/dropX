package com.fluid.dropx.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Size
import androidx.collection.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One shared instance for the whole app (service + UI), obtained with [ThumbnailManager.get].
 * Sharing it keeps a single memory cache and a single, correct disk-quota counter.
 */
class ThumbnailManager private constructor(private val context: Context) {

    companion object {
        private const val THUMB_SIZE = 256

        @Volatile
        private var instance: ThumbnailManager? = null

        fun get(context: Context): ThumbnailManager =
            instance ?: synchronized(this) {
                instance ?: ThumbnailManager(context.applicationContext).also { instance = it }
            }
    }

    // Memory Cache: up to 1/16th of the app's available JVM memory pool to hold active images in RAM.
    private val memoryCache = object : LruCache<String, ByteArray>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    // Tracks simultaneous incoming requests for the exact same thumbnail image.
    class GenerationState {
        val mutex = Mutex()
        val refCount = AtomicInteger(0)
    }

    private val activeGenerations = ConcurrentHashMap<String, GenerationState>()

    // Throttler: at most 3 parallel thumbnail generations so the CPU isn't flooded
    // when a web client requests hundreds of grid items at once.
    private val globalGenLimit = Semaphore(3)

    // Disk layer bookkeeping
    private val currentDiskUsage = AtomicLong(0L)
    private val diskQuota = 50 * 1024 * 1024L // Max 50 MB of cached thumbnails
    private val diskMutex = Mutex()

    init {
        val thumbDir = File(context.cacheDir, "thumbs")
        if (thumbDir.exists()) {
            currentDiskUsage.set(thumbDir.listFiles()?.filter { it.name.endsWith(".thumb") }?.sumOf { it.length() } ?: 0L)
        }
    }

    // Always runs on the IO pool so blocking decode/disk work never ties up Ktor's request threads
    suspend fun getThumbnail(name: String, uri: android.net.Uri, size: Long, lastModified: Long): ByteArray? =
        withContext(Dispatchers.IO) { loadOrGenerate(name, uri, size, lastModified) }

    private suspend fun loadOrGenerate(name: String, uri: android.net.Uri, size: Long, lastModified: Long): ByteArray? {
        val hash = CacheKeyGenerator.generate(name, size, lastModified)

        // 1. Fast path: memory cache
        memoryCache.get(hash)?.let { return it }

        // 2. Single-flight: concurrent requests for the same file wait on one generation
        val state = activeGenerations.compute(hash) { _, existing ->
            (existing ?: GenerationState()).apply { refCount.incrementAndGet() }
        }!!

        return try {
            state.mutex.withLock {
                // Double-check right after acquiring the lock
                memoryCache.get(hash)?.let { return@withLock it }

                val cacheFile = File(context.cacheDir, "thumbs/$hash.thumb")

                // 3. Persistent disk cache
                if (cacheFile.exists()) {
                    val bytes = diskMutex.withLock {
                        // "Touch" the file so pruning removes the least RECENTLY USED, not the oldest created
                        runCatching { cacheFile.setLastModified(System.currentTimeMillis()) }
                        cacheFile.readBytes()
                    }
                    memoryCache.put(hash, bytes)
                    return@withLock bytes
                }

                // 4. Miss: wait for a slot, then generate
                globalGenLimit.withPermit {
                    generateAndStore(hash, uri, cacheFile)
                }
            }
        } finally {
            activeGenerations.compute(hash) { _, existing ->
                if (existing != null && existing.refCount.decrementAndGet() == 0) null else existing
            }
        }
    }

    private suspend fun generateAndStore(hash: String, uri: android.net.Uri, cacheFile: File): ByteArray? {
        return try {
            val bitmap = loadBitmap(uri) ?: return null

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            bitmap.recycle()
            val bytes = out.toByteArray()

            diskMutex.withLock {
                cacheFile.parentFile?.mkdirs()
                // If we overwrite an existing file, only count the difference
                val previous = if (cacheFile.exists()) cacheFile.length() else 0L
                cacheFile.writeBytes(bytes)
                val newSize = currentDiskUsage.addAndGet(bytes.size.toLong() - previous)
                if (newSize > diskQuota) {
                    pruneDiskCache()
                }
            }

            memoryCache.put(hash, bytes)
            bytes
        } catch (e: CancellationException) {
            throw e              // never swallow coroutine cancellation
        } catch (e: Exception) {
            null
        }
    }

    // Images / documents: the system thumbnail service. Videos without one: a scaled frame.
    private fun loadBitmap(uri: android.net.Uri): Bitmap? {
        try {
            return context.contentResolver.loadThumbnail(uri, Size(THUMB_SIZE, THUMB_SIZE), null)
        } catch (e: Exception) {
            // fall through to the video-frame fallback
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // Decodes straight to thumbnail size (keeps aspect ratio) instead of a full 4K frame
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_SIZE, THUMB_SIZE)
                ?: retriever.getFrameAtTime(0)?.let { downscale(it) }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun downscale(src: Bitmap): Bitmap {
        val scale = THUMB_SIZE.toFloat() / maxOf(src.width, src.height)
        if (scale >= 1f) return src
        val out = Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (out !== src) src.recycle()
        return out
    }

    // Pruning: if we overshoot the 50 MB budget, delete least-recently-used files down to 80% (40 MB).
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