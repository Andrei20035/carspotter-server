-- ============================================================
-- Feed keyset (cursor) pagination support
-- ============================================================
-- The feed is ordered by (created_at DESC, id DESC) and paginated with the
-- keyset predicate:
--   (created_at < :cursorCreatedAt)
--   OR (created_at = :cursorCreatedAt AND id < :cursorPostId)
-- A composite index matching that ordering makes each page an index range scan.
-- The pre-existing idx_posts_created (created_at DESC) is left in place; it is a
-- prefix of this index and remains harmless/useful for created_at-only queries.

CREATE INDEX IF NOT EXISTS idx_posts_feed_keyset
    ON posts (created_at DESC, id DESC);
