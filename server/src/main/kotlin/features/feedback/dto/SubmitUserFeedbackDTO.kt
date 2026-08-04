package com.revio.server.features.feedback.dto

import com.revio.server.core.serialization.InstantSerializer
import com.revio.server.core.serialization.UUIDSerializer
import com.revio.server.features.feedback.ConfusionReason
import com.revio.server.features.feedback.FeedbackArea
import com.revio.server.features.feedback.FeedbackCategory
import com.revio.server.features.feedback.FeedbackPriority
import com.revio.server.features.feedback.FeedbackSource
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class SubmitUserFeedbackDTO(
    val category: FeedbackCategory,
    val area: FeedbackArea? = null,
    val message: String? = null,
    val secondaryMessage: String? = null,
    val quickReason: ConfusionReason? = null,
    val priority: FeedbackPriority? = null,
    val rating: Int? = null,
    val keepMessage: String? = null,
    val improveMessage: String? = null,
    val source: FeedbackSource,
    val originScreen: String? = null,
    val includeDiagnostics: Boolean = false,
    val appVersion: String? = null,
    val androidVersion: String? = null,
    val deviceModel: String? = null,
    val connectionType: String? = null,
    val lastErrorCode: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val clientFeedbackId: UUID,
    @Serializable(with = InstantSerializer::class)
    val clientSubmittedAt: Instant? = null,
)

@Serializable
data class UserFeedbackResponse(
    val status: String,
)
