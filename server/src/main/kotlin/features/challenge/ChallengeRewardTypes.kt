package com.revio.server.features.challenge

/** Whether a participant currently holds the challenge completion reward. */
enum class RewardState {
    NONE,
    GRANTED,
}

/** Direction of a challenge_reward_ledger entry. */
enum class LedgerEntryKind {
    GRANT,
    REVOKE,
}

/** Why a challenge_reward_ledger entry was written. */
enum class LedgerReason {
    /** contribution_count reached required_posts. */
    THRESHOLD_REACHED,

    /** A contributing post was deleted or invalidated, dropping progress below required_posts. */
    CONTRIBUTION_REMOVED,

    /** The challenge itself was cancelled (only possible before ends_at). */
    CHALLENGE_CANCELLED,

    /** Admin-initiated retroactive revoke-all for a misconfigured challenge, after ends_at. */
    ADMIN_REVOKE_ALL,
}
