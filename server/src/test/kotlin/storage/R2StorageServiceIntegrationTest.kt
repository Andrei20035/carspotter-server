package storage

import com.revio.server.core.storage.R2Config
import com.revio.server.core.storage.R2StorageService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MinIOContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.net.URI

/**
 * Integration test against a real S3-compatible server (MinIO), validating the
 * Put/Delete contract of R2StorageService without needing real Cloudflare R2 credentials.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2StorageServiceIntegrationTest {

    private lateinit var minio: MinIOContainer
    private lateinit var service: R2StorageService
    private lateinit var adminClient: S3Client

    private val bucket = "test-bucket"

    @BeforeAll
    fun setup() {
        minio = MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z")
        minio.start()

        adminClient = S3Client.builder()
            .endpointOverride(URI.create(minio.s3URL))
            .region(Region.of("us-east-1"))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(minio.userName, minio.password)
                )
            )
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(true).build()
            )
            .build()

        adminClient.createBucket(CreateBucketRequest.builder().bucket(bucket).build())

        val config = R2Config(
            accountId = "unused-with-endpoint-override",
            bucket = bucket,
            accessKeyId = minio.userName,
            secretAccessKey = minio.password,
            publicBaseUrl = "https://cdn.revio.app",
            endpointOverride = minio.s3URL,
        )
        service = R2StorageService(config)
    }

    @AfterAll
    fun teardown() {
        adminClient.close()
        minio.stop()
    }

    private fun getObjectBytes(key: String): ByteArray =
        adminClient.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray()

    @Test
    fun `uploadImage stores the bytes and returns a StoredImage matching the resolved URL`() = runTest {
        val key = "posts/2026/07/19/upload-test.jpg"
        val bytes = "fake-jpeg-bytes".toByteArray()

        val stored = service.uploadImage(bytes, key, "image/jpeg")

        assertEquals(key, stored.objectKey)
        assertEquals(service.resolveUrl(key), stored.url)
        assertEquals(bytes.size.toLong(), stored.sizeBytes)
        assertEquals(bytes.toList(), getObjectBytes(key).toList())
    }

    @Test
    fun `uploadImage on an existing key overwrites the object`() = runTest {
        val key = "posts/2026/07/19/overwrite-test.jpg"
        service.uploadImage("first".toByteArray(), key, "image/jpeg")

        service.uploadImage("second".toByteArray(), key, "image/jpeg")

        assertEquals("second", String(getObjectBytes(key)))
    }

    @Test
    fun `deleteImage removes the object so a subsequent get fails`() = runTest {
        val key = "posts/2026/07/19/delete-test.jpg"
        service.uploadImage("to-delete".toByteArray(), key, "image/jpeg")
        assertNotNull(getObjectBytes(key))

        service.deleteImage(key)

        assertThrows(NoSuchKeyException::class.java) { getObjectBytes(key) }
    }

    @Test
    fun `deleteImage on a non-existent key does not throw`() = runTest {
        service.deleteImage("posts/does/not/exist.jpg")
    }
}
