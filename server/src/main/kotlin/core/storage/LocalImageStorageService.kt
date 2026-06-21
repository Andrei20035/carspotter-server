package com.carspotter.core.storage

import java.net.URI
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
        if (objectKey.contains("://") && !objectKey.contains("/uploads/")) {
            return objectKey
        }

        val normalizedBaseUrl = publicBaseUrl.removeSuffix("/")
        val normalizedObjectKey = normalizeObjectKey(objectKey)
        return "$normalizedBaseUrl/uploads/$normalizedObjectKey"
    }

    override fun normalizeObjectKey(pathOrUrl: String): String {
        val trimmed = pathOrUrl.trim()
        val path = runCatching { URI(trimmed).path }.getOrNull()
            ?.takeIf { trimmed.contains("://") }
            ?: trimmed

        return path
            .substringAfter("/uploads/", path)
            .removePrefix("uploads/")
            .removePrefix("/")
    }

    private fun resolveSafePath(objectKey: String): Path {
        val normalizedObjectKey = normalizeObjectKey(objectKey)
        val resolved = baseDir.resolve(normalizedObjectKey).normalize()

        require(resolved.startsWith(baseDir.normalize())) {
            "Invalid object key: path escapes base directory"
        }

        return resolved
    }
}
