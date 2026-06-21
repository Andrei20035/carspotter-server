-- ============================================================
-- Reports
-- A user can report a post for a typed reason (incorrect car model,
-- duplicate, inappropriate content). One report per (user, post, reason)
-- so a user can't spam the same report; the insert is therefore idempotent.
-- ============================================================

CREATE TABLE reports (
                         id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         post_id     UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
                         reason      VARCHAR(30) NOT NULL,
                         status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                         created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         UNIQUE (reporter_id, post_id, reason)
);

CREATE INDEX idx_reports_post ON reports (post_id);
