package com.fluid.dropx.core

import java.security.MessageDigest

object CacheKeyGenerator {
    fun generate(name: String, size: Long, lastModified: Long): String {
        // Generates a unique MD5 string using file metadata.
        // If the name, size, or timestamp changes, the cache key breaks so we don't serve stale data.
        val input = "$name|$size|$lastModified"
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}