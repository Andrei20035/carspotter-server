package com.revio.server.features.feedback.dto

import kotlinx.serialization.Serializable

@Serializable
enum class PromptEvent { SHOWN, DISMISSED }

@Serializable
data class PromptStateUpdateDTO(
    val promptKey: String,
    val event: PromptEvent,
)
