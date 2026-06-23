package service

import com.carspotter.features.auth.RefreshTokenGenerator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RefreshTokenGeneratorTest {

    private val generator = RefreshTokenGenerator()

    @Test
    fun `generate returns a non-blank raw token`() {
        val (rawToken, _) = generator.generate()
        assertTrue(rawToken.isNotBlank())
    }

    @Test
    fun `generate returns a 64-char hex hash`() {
        val (_, hash) = generator.generate()
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, "hash must be lowercase hex")
    }

    @Test
    fun `generate produces different tokens on successive calls`() {
        val (raw1, _) = generator.generate()
        val (raw2, _) = generator.generate()
        assertNotEquals(raw1, raw2)
    }

    @Test
    fun `generate produces different hashes on successive calls`() {
        val (_, hash1) = generator.generate()
        val (_, hash2) = generator.generate()
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `hashOf is deterministic for same input`() {
        val (rawToken, hash) = generator.generate()
        assertEquals(hash, generator.hashOf(rawToken))
        assertEquals(hash, generator.hashOf(rawToken))
    }

    @Test
    fun `hashOf produces different results for different inputs`() {
        val h1 = generator.hashOf("token-a")
        val h2 = generator.hashOf("token-b")
        assertNotEquals(h1, h2)
    }

    @Test
    fun `raw token is url-safe base64 without padding`() {
        val (rawToken, _) = generator.generate()
        // URL-safe base64 without padding: only A-Z a-z 0-9 - _
        assertTrue(rawToken.all { it.isLetterOrDigit() || it == '-' || it == '_' },
            "raw token must be URL-safe base64 without padding, got: $rawToken")
        assertFalse(rawToken.contains('='), "raw token must not contain padding")
    }
}
