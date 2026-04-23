package com.carspotter.features.auth.dto

import kotlinx.serialization.Serializable

enum class OnboardingStep {
    PROFILE_REQUIRED, COMPLETED
}

@Serializable
data class AuthResponse(
    val token: String,
    val onboardingStep: OnboardingStep
)