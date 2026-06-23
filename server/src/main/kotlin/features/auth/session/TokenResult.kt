package com.carspotter.features.auth.session

import java.util.UUID

sealed class TokenResult {
    data class Valid(
        val sessionId: UUID,
        val credentialId: UUID,
        val userId: UUID?,
        val email: String,
        val version: Int,
        val scope: SessionScope,
        val isAdmin: Boolean,
    ) : TokenResult()

    object Expired : TokenResult()
    object Invalid : TokenResult()
    object SessionRevoked : TokenResult()
}
