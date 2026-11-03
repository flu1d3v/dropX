package com.fluid.dropx.network

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.Dispatchers
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Streams a content:// Uri and, crucially, supports REAL random access.
 *
 * Ktor's default ReadChannelContent.readFrom(range) opens the stream at byte 0 and
 * discards bytes up to range.first. For a 2 GB file that means reading ~1 GB just to
 * seek to the middle. Here we jump straight to the offset instead.
 */
class UriFileContent(
    private val context: Context,
    private val uri: Uri,
    override val contentType: ContentType,
    override val contentLength: Long
) : OutgoingContent.ReadChannelContent() {

    // Full file
    override fun readFrom(): ByteReadChannel = open(0L, contentLength - 1)

    // Range request (seeking / resume). Both ends inclusive.
    override fun readFrom(range: LongRange): ByteReadChannel = open(range.first, range.last)

    private fun open(start: Long, endInclusive: Long): ByteReadChannel {
        val length = (endInclusive - start + 1).coerceAtLeast(0L)
        val stream = openSeekable(start) ?: openBySkipping(start)
        val limited = LimitedInputStream(stream, length)
        // 256 KB buffer => far fewer syscalls than Ktor's 4 KB pool buffers
        val buffered = BufferedInputStream(limited, 256 * 1024)
        return buffered.toByteReadChannel(context = Dispatchers.IO)
    }

    /** Fast path: file descriptor + position(). Works for local/MediaStore/most SAF files. */
    private fun openSeekable(start: Long): InputStream? {
        var pfd: ParcelFileDescriptor? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            // AutoCloseInputStream closes the descriptor when the stream is closed
            val input = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            input.channel.position(start)
            input
        } catch (e: Exception) {
            // Not seekable (pipe, some cloud providers) -> fall back
            runCatching { pfd?.close() }
            null
        }
    }

    /** Slow fallback for providers that can't seek. Still correct, just not instant. */
    private fun openBySkipping(start: Long): InputStream {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Stream unavailable")
        var remaining = start
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
        return input
    }
}

/** Stops reading after [limit] bytes so a range response never overruns. */
private class LimitedInputStream(source: InputStream, private var limit: Long) : FilterInputStream(source) {
    override fun read(): Int {
        if (limit <= 0) return -1
        val b = super.read()
        if (b >= 0) limit--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (limit <= 0) return -1
        val n = super.read(b, off, minOf(len.toLong(), limit).toInt())
        if (n > 0) limit -= n
        return n
    }
}
