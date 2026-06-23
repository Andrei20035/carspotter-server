package com.carspotter.features.auth.dto

import kotlinx.serialization.Serializable

enum class OnboardingStep {
    PROFILE_REQUIRED, COMPLETED
}

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val scope: String,
    val onboardingStep: OnboardingStep
)
