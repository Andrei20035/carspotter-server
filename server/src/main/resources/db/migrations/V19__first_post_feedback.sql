CREATE TABLE first_post_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    feedback_type VARCHAR(40) NOT NULL DEFAULT 'first_post_experience',
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    quick_reason VARCHAR(40),
    comment VARCHAR(1000),
    surface VARCHAR(20),
    app_version VARCHAR(30),
    android_version VARCHAR(20),
    device_model VARCHAR(60),
    connection_type VARCHAR(20),
    upload_duration_ms INTEGER,
    had_retries BOOLEAN,
    last_error_code VARCHAR(60),
    client_submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX first_post_feedback_user_unique ON first_post_feedback(user_id, feedback_type);

CREATE TABLE feedback_prompt_state (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    prompt_key VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    shown_count SMALLINT NOT NULL DEFAULT 0,
    last_shown_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, prompt_key)
);
