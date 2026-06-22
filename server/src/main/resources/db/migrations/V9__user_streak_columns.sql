-- Streak tracking: how many consecutive local days the user posted at least one CAMERA-origin post.
ALTER TABLE users
    ADD COLUMN current_streak INT NOT NULL DEFAULT 0,
    ADD COLUMN longest_streak INT NOT NULL DEFAULT 0,
    -- Last local calendar date (YYYY-MM-DD) on which a camera post was counted toward the streak.
    -- NULL for users who have never posted a camera photo.
    ADD COLUMN last_streak_date DATE;
