-- ============================================================
-- Challenge progress and reward ledger.
--
-- challenge_contributions is the source of truth for progress: one row per
-- (challenge, post), UNIQUE-constrained so the same post can never be
-- counted twice.
--
-- challenge_participants is the serialization point: one row per
-- (challenge, user), locked with SELECT ... FOR UPDATE while evaluating
-- eligibility, so concurrent post creations for the same user/challenge
-- can't both trigger a grant. award_epoch changes on every revoke so a
-- later re-grant gets a fresh idempotency key.
--
-- challenge_reward_ledger is the audit trail for every grant/revoke, keyed
-- by idempotency_key so retries are absorbed rather than double-applied.
-- ============================================================

CREATE TABLE challenge_contributions (
                                          id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          challenge_id     UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
                                          user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                          post_id          UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
                                          car_model_id     UUID NOT NULL REFERENCES car_models(id) ON DELETE RESTRICT,
                                          post_created_at  TIMESTAMPTZ NOT NULL,
                                          created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

                                          UNIQUE (challenge_id, post_id)
);

CREATE INDEX idx_challenge_contributions_challenge_user
    ON challenge_contributions (challenge_id, user_id);

CREATE TABLE challenge_participants (
                                         id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                         challenge_id        UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
                                         user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                         contribution_count  INT NOT NULL DEFAULT 0,
                                         reward_state        VARCHAR(16) NOT NULL DEFAULT 'NONE',
                                         award_epoch         INT NOT NULL DEFAULT 0,
                                         first_completed_at  TIMESTAMPTZ,
                                         last_granted_at     TIMESTAMPTZ,
                                         updated_at          TIMESTAMPTZ,

                                         CONSTRAINT chk_challenge_participants_reward_state
                                             CHECK (reward_state IN ('NONE', 'GRANTED')),
                                         CONSTRAINT chk_challenge_participants_contribution_count_non_negative
                                             CHECK (contribution_count >= 0),
                                         UNIQUE (challenge_id, user_id)
);

CREATE TABLE challenge_reward_ledger (
                                          id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                          challenge_id     UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
                                          kind             VARCHAR(16) NOT NULL,
                                          reason           VARCHAR(32) NOT NULL,
                                          nominal_delta    INT NOT NULL,
                                          applied_delta    INT NOT NULL,
                                          award_epoch      INT NOT NULL,
                                          idempotency_key  VARCHAR(128) NOT NULL,
                                          created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

                                          CONSTRAINT chk_challenge_reward_ledger_kind
                                              CHECK (kind IN ('GRANT', 'REVOKE')),
                                          CONSTRAINT chk_challenge_reward_ledger_reason
                                              CHECK (reason IN (
                                                  'THRESHOLD_REACHED',
                                                  'CONTRIBUTION_REMOVED',
                                                  'CHALLENGE_CANCELLED',
                                                  'ADMIN_REVOKE_ALL'
                                              )),
                                          UNIQUE (idempotency_key)
);

CREATE INDEX idx_challenge_reward_ledger_user_challenge
    ON challenge_reward_ledger (user_id, challenge_id);
