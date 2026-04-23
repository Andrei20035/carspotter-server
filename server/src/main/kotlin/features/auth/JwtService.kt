package com.carspotter.features.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.UUID

class JwtService(
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String
) {
    fun generateJwtToken(
        credentialId: UUID,
        userId: UUID? = null,
        email: String,
        isAdmin: Boolean = false
    ): String {
        val tokenBuilder = JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("credentialId", credentialId.toString())
            .withClaim("email", email)
            .withClaim("isAdmin", isAdmin)
            .withExpiresAt(Date(System.currentTimeMillis() + 86400000))

        if (userId != null) {
            tokenBuilder.withClaim("userId", userId.toString())
        }

        return tokenBuilder.sign(Algorithm.HMAC256(jwtSecret))
    }
}