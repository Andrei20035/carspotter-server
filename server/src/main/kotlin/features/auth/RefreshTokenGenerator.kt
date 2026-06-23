package com.carspotter.features.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class RefreshTokenGenerator {

    /** Generates a (rawToken, sha256Hex) pair. The raw token is 256 bits of SecureRandom. */
    fun generate(): Pair<String, String> {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return Pair(rawToken, hashOf(rawToken))
    }

    /** SHA-256 of rawToken encoded as lowercase hex (64 chars). */
    fun hashOf(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
