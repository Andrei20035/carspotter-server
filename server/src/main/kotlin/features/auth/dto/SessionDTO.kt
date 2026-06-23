package com.carspotter.features.auth.dto

import com.carspotter.features.auth.session.AuthSession
import kotlinx.serialization.Serializable

@Serializable
data class SessionDTO(
    val id: String,
    val current: Boolean,
    val deviceId: String?,
    val deviceName: String?,
    val userAgent: String?,
    val createdAt: String,
    val lastUsedAt: String,
    val idleExpiresAt: String,
    val absoluteExpiresAt: String,
) {
    companion object {
        fun from(session: AuthSession, current: Boolean) = SessionDTO(
            id = session.id.toString(),
            current = current,
            deviceId = session.deviceId,
            deviceName = session.deviceName,
            userAgent = session.userAgent,
            createdAt = session.createdAt.toString(),
            lastUsedAt = session.lastUsedAt.toString(),
            idleExpiresAt = session.idleExpiresAt.toString(),
            absoluteExpiresAt = session.absoluteExpiresAt.toString(),
        )
    }
}
