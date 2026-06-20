-- ============================================================
-- Post location: human-readable town / country
-- ============================================================
-- Posts already store raw GPS (latitude, longitude). These columns hold the
-- reverse-geocoded place name shown in the feed ("Town, Country"). Both are
-- nullable: a post may be created before geocoding resolves, on a slow
-- connection, or when the user denies location access.

ALTER TABLE posts ADD COLUMN town    VARCHAR(100);
ALTER TABLE posts ADD COLUMN country VARCHAR(100);
