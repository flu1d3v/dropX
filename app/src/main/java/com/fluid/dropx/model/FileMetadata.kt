package com.fluid.dropx.model

import kotlinx.serialization.Serializable

@Serializable
data class FileMetadata(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String
)