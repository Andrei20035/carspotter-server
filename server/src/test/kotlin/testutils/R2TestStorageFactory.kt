package testutils

import com.revio.server.core.storage.R2Config
import com.revio.server.core.storage.R2StorageService
import org.testcontainers.containers.MinIOContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.net.URI

/**
 * Pornește un MinIO real în Docker și expune un R2StorageService (endpointOverride
 * spre MinIO) plus un client admin, pentru testele de rută care exercită fluxul
 * real de upload/delete pe provider-ul R2, fără credențiale R2 reale.
 *
 * Un singur container per JVM run.
 */
object R2TestStorageFactory {

    const val PUBLIC_BASE_URL = "https://cdn.revio.app"
    private const val BUCKET = "test-bucket"

    private var minio: MinIOContainer? = null
    private var adminClient: S3Client? = null
    private var storage: R2StorageService? = null
    private var started = false

    fun start() {
        if (started) return

        val container = MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z")
        container.start()
        minio = container

        val client = S3Client.builder()
            .endpointOverride(URI.create(container.s3URL))
            .region(Region.of("us-east-1"))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(container.userName, container.password)
                )
            )
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build())
        adminClient = client

        storage = R2StorageService(
            R2Config(
                accountId = "unused-with-endpoint-override",
                bucket = BUCKET,
                accessKeyId = container.userName,
                secretAccessKey = container.password,
                publicBaseUrl = PUBLIC_BASE_URL,
                endpointOverride = container.s3URL,
            )
        )

        started = true
    }

    fun stop() {
        adminClient?.close()
        adminClient = null
        storage = null
        minio?.stop()
        minio = null
        started = false
    }

    fun storageService(): R2StorageService = storage ?: error("R2TestStorageFactory not started")

    fun objectExists(key: String): Boolean {
        val client = adminClient ?: error("R2TestStorageFactory not started")
        return try {
            client.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(key).build())
            true
        } catch (e: NoSuchKeyException) {
            false
        }
    }

    fun keysWithPrefix(prefix: String): Set<String> {
        val client = adminClient ?: error("R2TestStorageFactory not started")
        return client.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).prefix(prefix).build())
            .contents()
            .map { it.key() }
            .toSet()
    }

    /** Strips the public base URL from a resolved URL, mirroring resolveUrl's inverse. */
    fun keyFromUrl(url: String): String = url.removePrefix("$PUBLIC_BASE_URL/")
}
