package com.revio.server.features.feedback.dto

import com.revio.server.core.serialization.InstantSerializer
import com.revio.server.features.feedback.PromptStatus
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class FeedbackPromptStateDTO(
    val promptKey: String,
    val status: PromptStatus,
    val shownCount: Int,
    @Serializable(with = InstantSerializer::class)
    val lastShownAt: Instant? = null,
)
