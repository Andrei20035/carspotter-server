-- ============================================================
-- Challenges
-- Global, admin-configured recurring events (e.g. weekend challenges).
-- starts_at/ends_at are absolute UTC instants; admin_timezone is kept only
-- for display/audit in the admin UI and never used to evaluate eligibility.
--
-- Only DRAFT, SCHEDULED and CANCELLED are persisted; ACTIVE/ENDED are
-- derived from starts_at/ends_at at request time — no scheduler needed.
--
-- Only one challenge may be SCHEDULED with an overlapping [starts_at, ends_at)
-- window at a time, enforced by the exclusion constraint below.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE challenges (
                            id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            title             VARCHAR(150) NOT NULL,
                            description       TEXT,
                            target_family_id  UUID NOT NULL REFERENCES car_families(id) ON DELETE RESTRICT,
                            required_posts    INT NOT NULL,
                            reward_points     INT NOT NULL,
                            starts_at         TIMESTAMPTZ NOT NULL,
                            ends_at           TIMESTAMPTZ NOT NULL,
                            admin_timezone    VARCHAR(64) NOT NULL,
                            status            VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
                            created_by        UUID REFERENCES users(id) ON DELETE SET NULL,
                            created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                            published_at      TIMESTAMPTZ,
                            cancelled_at      TIMESTAMPTZ,

                            CONSTRAINT chk_challenges_ends_after_starts CHECK (ends_at > starts_at),
                            CONSTRAINT chk_challenges_required_posts_positive CHECK (required_posts > 0),
                            CONSTRAINT chk_challenges_reward_points_positive CHECK (reward_points > 0),
                            CONSTRAINT chk_challenges_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'CANCELLED')),
                            CONSTRAINT chk_challenges_title_not_blank CHECK (LENGTH(TRIM(title)) > 0)
);

CREATE INDEX idx_challenges_status ON challenges (status);

ALTER TABLE challenges
    ADD CONSTRAINT excl_challenges_no_overlap
    EXCLUDE USING gist (
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'SCHEDULED');
