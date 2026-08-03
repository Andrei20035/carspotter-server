package com.revio.server.features.feedback.dto

import com.revio.server.core.serialization.InstantSerializer
import com.revio.server.features.feedback.FeedbackSurface
import com.revio.server.features.feedback.QuickReason
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SubmitFirstPostFeedbackDTO(
    val rating: Int,
    val quickReason: QuickReason? = null,
    val comment: String? = null,
    val surface: FeedbackSurface? = null,
    val appVersion: String? = null,
    val androidVersion: String? = null,
    val deviceModel: String? = null,
    val connectionType: String? = null,
    val uploadDurationMs: Int? = null,
    val hadRetries: Boolean? = null,
    val lastErrorCode: String? = null,
    @Serializable(with = InstantSerializer::class)
    val clientSubmittedAt: Instant? = null,
)
