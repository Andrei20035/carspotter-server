package com.revio.server.features.account_deletion

import kotlinx.serialization.Serializable

/**
 * Why a user is deleting their account. Names are part of the wire contract and the
 * `chk_deletion_feedback_reason` CHECK constraint (V18 migration) — keep them in sync.
 */
@Serializable
enum class DeletionReason {
    TOO_MANY_NOTIFICATIONS,
    NOT_INTERESTING_CARSPOTS,
    FOUND_BETTER_APP,
    PRIVACY_CONCERNS,
    TAKING_A_BREAK,
    OTHER,
}
