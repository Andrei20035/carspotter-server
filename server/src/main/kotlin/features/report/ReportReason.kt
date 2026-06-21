package com.carspotter.features.report

import kotlinx.serialization.Serializable

/**
 * Why a post is being reported. The enum names are part of the wire contract and must
 * stay identical to the Android client's `ReportReason`, so serialization maps 1:1.
 */
@Serializable
enum class ReportReason {
    /** The uploaded car does not match the selected brand/model. */
    INCORRECT_CAR_MODEL,

    /** The same car/photo has already been uploaded. */
    DUPLICATE_POST,

    /** Spam, offensive, or otherwise non-car content. */
    INAPPROPRIATE_CONTENT,
}

/** Moderation state of a report. New reports start as [PENDING]. */
enum class ReportStatus {
    PENDING,
    REVIEWED,
    DISMISSED,
}
