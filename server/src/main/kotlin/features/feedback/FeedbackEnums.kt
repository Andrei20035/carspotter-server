package com.revio.server.features.feedback

import kotlinx.serialization.Serializable

@Serializable
enum class QuickReason {
    UPLOAD_DIFFICULT,
    LOCATION_CONFUSING,
    CAR_DETAILS_CONFUSING,
    TOOK_TOO_LONG,
    SOMETHING_BROKE,
    UPLOAD_PROCESS,
    LOCATION,
    CAR_DETAILS,
    DESCRIPTION,
    POSTING_CONFIRMATION,
    EASY_TO_USE,
    FAST,
    CLEAR,
    FUN,
    LOOKS_GOOD,
    OTHER,
}

@Serializable
enum class PromptStatus {
    ELIGIBLE,
    DISMISSED_ONCE,
    DISMISSED_TWICE,
    SUBMITTED,
}

@Serializable
enum class FeedbackSurface {
    FEED,
    PROFILE,
}
