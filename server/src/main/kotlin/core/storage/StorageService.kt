package com.carspotter.core.storage

data class StoredImage(
    val objectKey: String,
    val url: String,
    val sizeBytes: Long
)

interface IStorageService {
    suspend fun uploadImage(
        bytes: ByteArray,
        objectKey: String,
        contentType: String
    ): StoredImage

    suspend fun deleteImage(objectKey: String)

    fun resolveUrl(objectKey: String): String
}