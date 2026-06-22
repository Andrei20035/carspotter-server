-- Record whether a post was taken directly from the camera or picked from the gallery.
-- Existing posts default to GALLERY so they never retroactively earn SpotScore.
ALTER TABLE posts
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'GALLERY';

-- IANA timezone ID supplied by the client at post creation time (e.g. "Europe/Bucharest").
-- Used by the backend to compute the user's local day for streak/daily-cap logic.
-- Nullable: older clients or network errors may omit it; backend falls back to UTC.
ALTER TABLE posts
    ADD COLUMN created_at_timezone VARCHAR(64);

-- Covering index for the daily-cap query: find a user's CAMERA posts on a given day.
CREATE INDEX idx_posts_user_source_created ON posts (user_id, source, created_at DESC);
