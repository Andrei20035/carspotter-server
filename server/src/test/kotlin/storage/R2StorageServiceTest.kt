package storage

import com.revio.server.core.storage.R2Config
import com.revio.server.core.storage.R2StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class R2StorageServiceTest {

    private val config = R2Config(
        accountId = "test-account",
        bucket = "test-bucket",
        accessKeyId = "test-access-key",
        secretAccessKey = "test-secret-key",
        publicBaseUrl = "https://cdn.revio.app",
    )

    private val service = R2StorageService(config)

    @Test
    fun `resolveUrl builds a public URL from a raw object key`() {
        val key = "posts/2026/07/19/abc.jpg"

        assertEquals("https://cdn.revio.app/posts/2026/07/19/abc.jpg", service.resolveUrl(key))
    }

    @Test
    fun `resolveUrl passes through an already-absolute external URL`() {
        val externalUrl = "https://lh3.googleusercontent.com/a/avatar.jpg"

        assertEquals(externalUrl, service.resolveUrl(externalUrl))
    }

    @Test
    fun `normalizeObjectKey leaves a raw key unchanged`() {
        val key = "posts/2026/07/19/abc.jpg"

        assertEquals(key, service.normalizeObjectKey(key))
    }

    @Test
    fun `normalizeObjectKey strips the public base URL from a full URL`() {
        val fullUrl = "https://cdn.revio.app/posts/2026/07/19/abc.jpg"

        assertEquals("posts/2026/07/19/abc.jpg", service.normalizeObjectKey(fullUrl))
    }

    @Test
    fun `resolveUrl is idempotent through a normalize round-trip`() {
        val key = "posts/2026/07/19/abc.jpg"
        val url = service.resolveUrl(key)

        assertEquals(url, service.resolveUrl(service.normalizeObjectKey(url)))
    }
}
