CREATE TABLE auth_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id       UUID NOT NULL REFERENCES auth_credentials(id) ON DELETE CASCADE,
    user_id             UUID REFERENCES users(id) ON DELETE CASCADE,
    version             INTEGER NOT NULL DEFAULT 1,
    scope               VARCHAR(20) NOT NULL DEFAULT 'FULL',
    refresh_token_hash  CHAR(64) NOT NULL,
    prev_token_hash     CHAR(64),
    prev_rotated_at     TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    revoked_reason      VARCHAR(40),
    device_id           VARCHAR(128),
    device_name         VARCHAR(128),
    user_agent          TEXT,
    ip_address          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    idle_expires_at     TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_session_status CHECK (
        status IN ('ACTIVE', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT chk_session_scope CHECK (
        scope IN ('ONBOARDING', 'FULL')
    ),
    CONSTRAINT chk_session_full_has_user CHECK (
        scope <> 'FULL' OR user_id IS NOT NULL
    ),
    CONSTRAINT chk_revoked_reason CHECK (
        status <> 'REVOKED' OR revoked_reason IN (
            'SUPERSEDED', 'LOGOUT', 'LOGOUT_ALL',
            'PASSWORD_CHANGED', 'ACCOUNT_DELETED',
            'REFRESH_TOKEN_REUSED', 'IDLE_EXPIRED', 'ABSOLUTE_EXPIRED'
        )
    )
);

-- Cel mult o sesiune ACTIVE per credential
CREATE UNIQUE INDEX uq_active_session_per_credential
    ON auth_sessions (credential_id) WHERE status = 'ACTIVE';

-- Lookup O(1) pe hash curent
CREATE UNIQUE INDEX uq_sessions_refresh_hash
    ON auth_sessions (refresh_token_hash);

-- Lookup pentru grace window (tokenul precedent)
CREATE INDEX idx_sessions_prev_hash
    ON auth_sessions (prev_token_hash) WHERE prev_token_hash IS NOT NULL;

-- Queries per credential
CREATE INDEX idx_sessions_credential
    ON auth_sessions (credential_id);
