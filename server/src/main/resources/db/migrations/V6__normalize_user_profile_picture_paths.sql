UPDATE users
SET profile_picture_path = regexp_replace(profile_picture_path, '^.*?/uploads/', '')
WHERE profile_picture_path IS NOT NULL
  AND profile_picture_path LIKE '%/uploads/%';

UPDATE users
SET profile_picture_path = regexp_replace(profile_picture_path, '^uploads/', '')
WHERE profile_picture_path IS NOT NULL
  AND profile_picture_path LIKE 'uploads/%';
