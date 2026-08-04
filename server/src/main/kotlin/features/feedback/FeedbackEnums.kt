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

@Serializable
enum class FeedbackCategory {
    NOT_WORKING,
    CONFUSING,
    FEATURE_IDEA,
    GENERAL,
}

@Serializable
enum class FeedbackArea {
    POSTING,
    FEED,
    PROFILE,
    ACTIVITY,
    LEADERBOARD,
    SETTINGS,
    AUTHENTICATION,
    NAVIGATION,
    NEW_AREA,
    NOT_SURE,
    OTHER,
}

@Serializable
enum class FeedbackPriority {
    NICE_TO_HAVE,
    IMPORTANT,
    BLOCKING,
}

@Serializable
enum class ConfusionReason {
    DIDNT_KNOW_WHAT_TO_DO_NEXT,
    WORDING_NOT_CLEAR,
    COULDNT_FIND_SOMETHING,
    UNEXPECTED_RESULT,
    TOO_MUCH_INFORMATION,
    OTHER,
}

@Serializable
enum class FeedbackSource {
    SETTINGS_FEEDBACK,
    FIRST_POST_PROMPT,
}

@Serializable
enum class UserFeedbackStatus {
    NEW,
    REVIEWING,
    NEED_MORE_INFORMATION,
    PLANNED,
    FIXED,
    CLOSED,
}
