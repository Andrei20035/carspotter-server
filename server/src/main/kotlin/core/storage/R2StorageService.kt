package com.revio.server.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

class R2StorageService(
    private val config: R2Config,
) : IStorageService {

    private val client: S3Client = S3Client.builder()
        .endpointOverride(
            URI.create(config.endpointOverride ?: "https://${config.accountId}.r2.cloudflarestorage.com")
        )
        .region(Region.of("auto"))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKeyId, config.secretAccessKey)
            )
        )
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()
        )
        .build()

    override suspend fun uploadImage(
        bytes: ByteArray,
        objectKey: String,
        contentType: String
    ): StoredImage = withContext(Dispatchers.IO) {
        val key = normalizeObjectKey(objectKey)

        val request = PutObjectRequest.builder()
            .bucket(config.bucket)
            .key(key)
            .contentType(contentType)
            .build()

        client.putObject(request, RequestBody.fromBytes(bytes))

        StoredImage(
            objectKey = key,
            url = resolveUrl(key),
            sizeBytes = bytes.size.toLong()
        )
    }

    override suspend fun deleteImage(objectKey: String) {
        withContext(Dispatchers.IO) {
            val key = normalizeObjectKey(objectKey)
            val request = DeleteObjectRequest.builder()
                .bucket(config.bucket)
                .key(key)
                .build()

            try {
                client.deleteObject(request)
            } catch (e: NoSuchKeyException) {
                // already absent; deleteImage is idempotent
            }
        }
    }

    override fun resolveUrl(objectKey: String): String {
        if (objectKey.contains("://") && !objectKey.startsWith(config.publicBaseUrl)) {
            return objectKey
        }

        val normalizedObjectKey = normalizeObjectKey(objectKey)
        return "${config.publicBaseUrl}/$normalizedObjectKey"
    }

    override fun normalizeObjectKey(pathOrUrl: String): String {
        val trimmed = pathOrUrl.trim()
        val path = runCatching { URI(trimmed).path }.getOrNull()
            ?.takeIf { trimmed.contains("://") }
            ?: trimmed

        return path.removePrefix("/")
    }
}
