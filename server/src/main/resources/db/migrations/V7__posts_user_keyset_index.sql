-- Covering index for the per-user keyset (cursor) pagination query:
-- WHERE user_id = ? AND (created_at < ? OR (created_at = ? AND id < ?))
-- ORDER BY created_at DESC, id DESC LIMIT ?
CREATE INDEX IF NOT EXISTS idx_posts_user_created_id
    ON posts (user_id, created_at DESC, id DESC);
