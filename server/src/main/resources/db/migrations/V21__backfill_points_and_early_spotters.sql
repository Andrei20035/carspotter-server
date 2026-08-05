-- Backfill: existing posts get 10 points each, users.spot_score is recalculated to match,
-- and all existing users are remapped to deterministic, consecutive early_spotter_number values.

DO $$
DECLARE
    user_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO user_count FROM users;
    IF user_count > 1000 THEN
        RAISE EXCEPTION 'V21 backfill aborted: % existing users exceeds the 1000 early-spotter slot limit', user_count;
    END IF;
END $$;

UPDATE posts SET points = 10;

UPDATE users u
   SET spot_score = COALESCE(
       (SELECT SUM(p.points) FROM posts p WHERE p.user_id = u.id), 0);

UPDATE users SET is_early_spotter = FALSE, early_spotter_number = NULL;

WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at ASC, id ASC) AS n
      FROM users
)
UPDATE users u
   SET is_early_spotter = TRUE, early_spotter_number = o.n
  FROM ordered o
 WHERE u.id = o.id;

UPDATE early_spotter_counter
   SET last_assigned = (SELECT COALESCE(MAX(early_spotter_number), 0) FROM users)
 WHERE id = 1;
