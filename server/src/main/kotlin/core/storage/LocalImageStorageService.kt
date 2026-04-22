package com.carspotter.core.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class LocalImageStorageService(
    private val baseDir: Path,
    private val publicBaseUrl: String
) : IStorageService {

    override suspend fun uploadImage(
        bytes: ByteArray,
        objectKey: String,
        contentType: String
    ): StoredImage {
        val targetPath = resolveSafePath(objectKey)

        targetPath.parent?.let { Files.createDirectories(it) }
        Files.write(targetPath, bytes)

        return StoredImage(
            objectKey = objectKey,
            url = resolveUrl(objectKey),
            sizeBytes = bytes.size.toLong()
        )
    }

    override suspend fun deleteImage(objectKey: String) {
        val path = resolveSafePath(objectKey)
        path.deleteIfExists()
    }

    override fun resolveUrl(objectKey: String): String {
        val normalizedBaseUrl = publicBaseUrl.removeSuffix("/")
        val normalizedObjectKey = objectKey.removePrefix("/")
        return "$normalizedBaseUrl/uploads/$normalizedObjectKey"
    }

    private fun resolveSafePath(objectKey: String): Path {
        val normalizedObjectKey = objectKey.removePrefix("/")
        val resolved = baseDir.resolve(normalizedObjectKey).normalize()

        require(resolved.startsWith(baseDir.normalize())) {
            "Invalid object key: path escapes base directory"
        }

        return resolved
    }
}