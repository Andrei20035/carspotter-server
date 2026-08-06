package com.revio.server.features.challenge

/**
 * Only the states an admin can put a challenge into are persisted. ACTIVE/ENDED are derived
 * from (status == SCHEDULED, starts_at, ends_at) at request time — see [effectiveStatus] in
 * ChallengeService (Etapa 3) — so no scheduler is needed to flip a challenge live or closed.
 */
enum class ChallengeStatus {
    DRAFT,
    SCHEDULED,
    CANCELLED,
}
