# CarSpotter — Seed Data

Seed content for the **local PostgreSQL dev/test database**. The JSON files +
`compressed/` images are imported by the Kotlin seeder
(`src/main/kotlin/seed/SeedImporter.kt`), which inserts rows in the documented
order and maps the logical `*_ref` keys to the real database-generated UUIDs.

### Run the importer

```
# 1. start the local Postgres (creates carspotter_dev on first init)
docker compose up -d postgres

# 2. import (reads server/.env, runs Flyway, inserts auth -> users -> posts)
./gradlew seed
```

The seeder is **idempotent**: on each run it first removes any previously-seeded
accounts (matched by email) and their uploaded images, then re-inserts. It reuses
the server's own Exposed tables, BCrypt (cost 12) and `LocalImageStorageService`,
so the rows are indistinguishable from real app data.

---

## Folder structure

```
seed/
├── README.md                     (this file)
├── seed_auth_credentials.json    auth_credentials rows
├── seed_users.json               users rows (profile data)
├── seed_posts.json               posts rows
├── profile_pictures/             10 RAW profile images (originals, untouched)
├── posts/                        42 RAW car post images (originals, untouched)
├── compressed/                   client-equivalent compressed images (import from here)
│   ├── profile_pictures/         512² max, JPEG q80, 1:1 center-crop
│   └── posts/                    1080x1350 max, JPEG q82, 4:5 center-crop
└── tools/
    └── compress_seed_images.py   reproduces the Android ImageCompressor
```

> The importer should read images from **`compressed/`**, not from the raw
> folders. The raw originals are kept only as the source for re-compression.

### `profile_pictures/`
One image per user. Files are named after the person/username (e.g.
`alexp21.jpg`, `mihnea.jpg`, `carina.jpg`); the exact filename for each user is
given by that record's `profile_picture_path` in `seed_users.json`. The real
image files are already present.

### `posts/`
42 car images named `post1.jpg` … `post42.jpg` (matching `image_path` in
`seed_posts.json`). The real image files are already present.

### `compressed/` + `tools/compress_seed_images.py`
The server does **no** image processing — compression is done **client-side** by
the Android app (`core/image/ImageCompressor.kt`). Since the seed bypasses the
client, we reproduce that step offline so seed images look like real uploads.

`tools/compress_seed_images.py` (Pillow) mirrors `ImageCompressor` exactly:
apply EXIF orientation → center-crop to aspect (profile 1:1, post 4:5) → resize
within max (never upscales) → JPEG encode.

| | profile | post |
|---|---|---|
| max size | 512×512 | 1080×1350 |
| aspect crop | 1:1 | 4:5 |
| JPEG quality | 80 | 82 |

Run (uses the project venv):

```
venv/bin/python seed/tools/compress_seed_images.py
```

**Parity caveat:** Android's JPEG encoder (Skia) ≠ Pillow/libjpeg, so output is
**not byte-identical** to a real device upload — dimensions, aspect-crop and
format match; file sizes are in the same ballpark.

**Low-resolution sources (observed):** the compressor never upscales (faithful to
the app), so small originals stay small. All 10 profile pictures are **200×200**
(source avatars are 200px; the 512 cap is never reached). A few posts stay below
1080×1350 — notably `post22` (580×725), `post33` (750×938), `post20/25/26`
(~930×1160). To get sharper images, replace those raw originals with ≥ target-size
versions and re-run the script.

---

## JSON files

### `seed_auth_credentials.json`
One record per account → one row in `auth_credentials`.

| field            | maps to                  | notes |
|------------------|--------------------------|-------|
| `auth_ref`       | (logical key, not a column) | stable reference, e.g. `auth_test_1` |
| `email`          | `email`                  | unique, already lowercase |
| `provider`       | `provider`               | always `REGULAR` here |
| `google_id`      | `google_id`              | `null` (required null for REGULAR) |
| `password_plain` | — (metadata only)        | the plaintext password; **must not** be stored as-is |
| `password_hash`  | `password`               | currently `null`; importer fills this with a BCrypt hash |

**Password handling — important.** Normal registration
(`AuthService.createCredentials`) hashes passwords with **BCrypt, cost factor 12**
(`at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, …)`).
The DB stores only the hash. Therefore the importer **must** BCrypt-hash
`password_plain` (cost 12) and write the result into the `password` column.
`password_plain` is metadata to let the importer (or backend code) produce the
hash; do **not** insert it into the database directly. All 10 accounts use the
same plaintext: `test123.`

### `seed_users.json`
One record per user → one row in `users`. Linked to an auth row via `auth_ref`.

| field                  | maps to                | notes |
|------------------------|------------------------|-------|
| `user_ref`             | (logical key)          | e.g. `user_test_1` |
| `auth_ref`             | `auth_credential_id`   | importer resolves to the inserted auth UUID |
| `full_name`            | `full_name`            | ≤ 150 chars |
| `username`             | `username`             | **lowercase**, `^[a-z0-9._]+$`, 3–50 chars (see assumptions) |
| `country`              | `country`              | always `Romania` |
| `birth_date`           | `birth_date`           | ISO `YYYY-MM-DD`, in the past |
| `phone_number`         | `phone_number`         | nullable; currently `null` for all users |
| `profile_picture_path` | `profile_picture_path` | points into `seed/profile_pictures/` (filename per user) |
| `spot_score`           | `spot_score`           | `0` (recompute from posts later if desired) |
| `gender`               | — (metadata only)      | used only to pick names/usernames; no column exists |

3 users are female (`user_test_6`, `user_test_7`, `user_test_10`), 7 are male.

### `seed_posts.json`
One record per post → one row in `posts`. Linked to a user via `user_ref`.

| field           | maps to        | notes |
|-----------------|----------------|-------|
| `post_ref`      | (logical key)  | `post1` … `post42` |
| `user_ref`      | `user_id`      | importer resolves to the inserted user UUID |
| `image_path`    | `image_path`   | points into `seed/posts/`; required |
| `car_model_id`  | `car_model_id` | always `null` (see assumptions) |
| `custom_brand`  | `custom_brand` | required because `car_model_id` is null |
| `custom_model`  | `custom_model` | required because `car_model_id` is null |
| `description`   | `description`  | non-blank, ≤ 1000 chars |
| `latitude`      | `latitude`     | spotting locations across Europe (incl. Bucharest); valid −90..90 |
| `longitude`     | `longitude`    | spotting locations across Europe (incl. Bucharest); valid −180..180 |
| `created_at`    | `created_at`   | ISO-8601 UTC, spread across ~14 recent days |

Post distribution (42 total): `test1` = 5, `test2` = 5, `test3`–`test10` = 4 each.

---

## Insertion order

Foreign keys require this exact order:

1. **`auth_credentials`** — insert from `seed_auth_credentials.json`.
   Capture each returned UUID, keyed by `auth_ref`.
2. **`users`** — insert from `seed_users.json`, resolving `auth_ref` →
   `auth_credential_id`. Capture each returned UUID, keyed by `user_ref`.
3. **`posts`** — insert from `seed_posts.json`, resolving `user_ref` → `user_id`.

`auth_credential_id` is **unique and required** on `users`, and `user_id` is
**required** on `posts`, so the dependency chain is auth → user → post.

---

## How the real registration flow works (for context)

- `POST /auth/register` creates **only** the `auth_credentials` row and returns a
  JWT plus `onboardingStep = PROFILE_REQUIRED`. **No `users` row is created here.**
- The `users` row is created in a **separate profile-completion step**:
  `POST /users` (JWT-authenticated), via `UserService.createUserProfile`.
- So a fully usable account = 1 auth row **and** 1 user row. This seed builds
  both directly, skipping the HTTP layer.

---

## Assumptions made

1. **Seed location.** Placed under `server/seed/` since this targets the backend's
   local PostgreSQL test database (`server/docker-compose.yml`).
2. **Passwords are hashed by the importer** with BCrypt cost 12 to match
   `AuthService`. Plaintext lives only in `password_plain` metadata.
3. **Usernames are stored lowercase.** `UserService.normalizeUsername` lowercases
   input and enforces `^[a-z0-9._]+$` (3–50 chars). To match what app-created
   accounts actually store, seed usernames are already lowercase
   (`alexp21`, `mihnea.gt`, `lucas.spots`, …). Display capitalization lives in
   `full_name`.
4. **Posts use `custom_brand` + `custom_model`, not `car_model_id`.** The
   `car_models` table is empty in a fresh DB and has DB-generated UUIDs, so seeding
   `car_model_id` would require seeding `car_models` first and threading UUIDs
   through. The schema's `chk_post_car_source` constraint allows the custom-source
   branch (`car_model_id IS NULL AND custom_brand/custom_model NOT NULL`), so this
   is valid. If you later want real `car_model_id` references, add a
   `seed_car_models.json` + an extra insertion step (step 0) and flip the posts.
5. **`spot_score` starts at 0** for everyone (can be recomputed from posts later).
6. **`role`** is not in the seed (see warnings) — the DB default `'USER'` applies.
7. **`created_at`** values are explicit so the feed (ordered `created_at DESC,
   id DESC`) looks natural; they span ~14 days back from 2026-06-19 12:00 UTC.
8. **Coordinates span real car-spotting locations across Europe** (Bucharest,
   Monaco, Paris, Rome, Milan, Vienna, Munich, Cluj, Timișoara, Iași, …), not
   only Bucharest. All values are within valid lat/lon ranges.
9. **Logical refs, not UUIDs.** No DB-generated UUIDs are hardcoded. The importer
   inserts, captures returned UUIDs, and resolves `auth_ref` / `user_ref`.

---

## ⚠️ Warnings — schema notes the importer must respect

- **`users.role` mismatch (SQL vs Exposed).** The SQL migration
  (`V1__init.sql`) defines `users.role VARCHAR(20) NOT NULL DEFAULT 'USER'`
  with a `CHECK (role IN ('USER','ADMIN'))`, but the Exposed `UserTable`
  (`features/user/UserTable.kt`) has **no `role` column at all**. This is a real
  source-of-truth divergence. It is **not blocking** for seeding because the
  column has a DB default — raw SQL/Exposed inserts that omit `role` get `'USER'`.
  Only flagged so it's known; the seed does not set `role`.

- **Constraints the data already satisfies (do not violate on import):**
  - `auth_credentials`: REGULAR ⇒ `password` NOT NULL **and** `google_id` NULL
    (`provider_consistency_check`). The importer must fill `password` with the
    BCrypt hash — inserting a REGULAR row with null password will fail.
  - `users`: `auth_credential_id` required + unique; `username` unique
    **case-insensitively** (`idx_users_username_lower`) — all seed usernames are
    distinct and lowercase; `birth_date <= CURRENT_DATE`.
  - `posts`: `user_id` and `image_path` required; latitude/longitude are **both
    present** here (`chk_posts_location_pair`); exactly one car source is set
    (custom only); `description` non-blank and ≤ 1000 chars.

- **No `car_models` seeded.** If a future requirement needs posts tied to real
  `car_model_id`s, seed `car_models` first; otherwise keep the custom-source posts.

- **Image files are present.** `profile_pictures/` holds the 10 profile images
  and `posts/` holds the 42 car images, named exactly as referenced by the JSON
  (`profile_picture_path` / `image_path`). The importer should read/upload them
  from these paths.
