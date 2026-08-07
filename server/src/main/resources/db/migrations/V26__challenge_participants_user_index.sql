-- ============================================================
-- Index for per-user challenge history lookups.
--
-- challenge_participants' only existing index is UNIQUE(challenge_id, user_id),
-- which does not serve a WHERE user_id = ? query (challenge_id leads the
-- composite). This index supports listing/aggregating a single user's
-- challenge participation without a table scan.
-- ============================================================

CREATE INDEX idx_challenge_participants_user
    ON challenge_participants (user_id);
