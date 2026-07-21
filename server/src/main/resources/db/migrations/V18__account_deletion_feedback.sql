CREATE TABLE account_deletion_feedback (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reason             VARCHAR(40) NOT NULL,
    details            TEXT,
    account_age_days   INTEGER,
    post_count         INTEGER,
    spot_score         INTEGER,
    streak_days        INTEGER,
    provider           VARCHAR(20),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_deletion_feedback_reason CHECK (
        reason IN (
            'TOO_MANY_NOTIFICATIONS', 'NOT_INTERESTING_CARSPOTS', 'FOUND_BETTER_APP',
            'PRIVACY_CONCERNS', 'TAKING_A_BREAK', 'OTHER'
        )
    )
);
