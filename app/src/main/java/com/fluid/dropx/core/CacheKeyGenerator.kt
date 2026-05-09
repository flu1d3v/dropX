package com.fluid.dropx.core

import java.security.MessageDigest

object CacheKeyGenerator {
    fun generate(name: String, size: Long, lastModified: Long): String {
        val input = "$name|$size|$lastModified"
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}