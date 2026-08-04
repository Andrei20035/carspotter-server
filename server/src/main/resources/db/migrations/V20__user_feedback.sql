CREATE TABLE user_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(32) NOT NULL,
    area VARCHAR(32),
    message VARCHAR(4000) NOT NULL,
    secondary_message VARCHAR(4000),
    quick_reason VARCHAR(40),
    priority VARCHAR(20),
    rating SMALLINT CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    keep_message VARCHAR(2000),
    improve_message VARCHAR(2000),
    source VARCHAR(32) NOT NULL,
    origin_screen VARCHAR(60),
    include_diagnostics BOOLEAN NOT NULL DEFAULT FALSE,
    app_version VARCHAR(30),
    android_version VARCHAR(20),
    device_model VARCHAR(60),
    connection_type VARCHAR(20),
    last_error_code VARCHAR(60),
    screenshot_key TEXT,
    client_feedback_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'NEW',
    client_submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_user_feedback_client_id ON user_feedback(client_feedback_id);
CREATE INDEX ix_user_feedback_category_created ON user_feedback(category, created_at DESC);
CREATE INDEX ix_user_feedback_user_created ON user_feedback(user_id, created_at DESC);
