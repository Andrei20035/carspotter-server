CREATE TABLE activity_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(24) NOT NULL,      -- STREAK | LEADERBOARD_UP
    event_date  DATE NOT NULL,
    value_int   INT  NOT NULL,             -- streakDays sau placesMoved
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_activity_events_user_type_date
    ON activity_events (user_id, type, event_date);

CREATE INDEX ix_activity_events_user_created
    ON activity_events (user_id, created_at DESC);
