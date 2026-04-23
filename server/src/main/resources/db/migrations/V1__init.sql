CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- Auth
-- ============================================================

CREATE TABLE auth_credentials (
                                  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  email      VARCHAR(255) UNIQUE NOT NULL,
                                  password   TEXT,
                                  provider   VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
                                  google_id  TEXT,
                                  CONSTRAINT provider_consistency_check CHECK (
                                      (provider = 'REGULAR' AND google_id IS NULL AND password IS NOT NULL) OR
                                      (provider = 'GOOGLE'  AND google_id IS NOT NULL AND password IS NULL)
                                      )
);

-- ============================================================
-- Users
-- ============================================================

CREATE TABLE users (
                       id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       auth_credential_id   UUID UNIQUE NOT NULL REFERENCES auth_credentials(id) ON DELETE CASCADE,
                       profile_picture_path TEXT,
                       full_name            VARCHAR(150) NOT NULL,
                       phone_number         VARCHAR(20),
                       birth_date           DATE NOT NULL,
                       username             VARCHAR(50) NOT NULL,
                       country              VARCHAR(50) NOT NULL,
                       spot_score           INTEGER NOT NULL DEFAULT 0,
                       role                 VARCHAR(20) NOT NULL DEFAULT 'USER',
                       created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT chk_username_not_blank
                           CHECK (LENGTH(TRIM(username)) > 0),

                       CONSTRAINT chk_spot_score_non_negative
                           CHECK (spot_score >= 0),

                       CONSTRAINT chk_birth_date_valid
                           CHECK (birth_date <= CURRENT_DATE),

                       CONSTRAINT chk_user_role
                           CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX idx_users_username_lower
    ON users (LOWER(username));

-- ============================================================
-- Car models
-- ============================================================

CREATE TABLE car_models (
                            id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            brand      VARCHAR(50) NOT NULL,
                            model      VARCHAR(50) NOT NULL,
                            UNIQUE (brand, model)
);

-- ============================================================
-- Posts
-- ============================================================

CREATE TABLE posts (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                       car_model_id  UUID REFERENCES car_models(id) ON DELETE RESTRICT,
                       custom_brand  VARCHAR(50),
                       custom_model  VARCHAR(80),
                       image_path    TEXT NOT NULL,
                       description   TEXT,
                       latitude      DOUBLE PRECISION,
                       longitude     DOUBLE PRECISION,
                       created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       CONSTRAINT chk_post_car_source CHECK (
                           (
                               car_model_id IS NOT NULL
                               AND custom_brand IS NULL
                               AND custom_model IS NULL
                           )
                           OR
                           (
                               car_model_id IS NULL
                               AND custom_brand IS NOT NULL
                               AND custom_model IS NOT NULL
                           )
                       ),

                       CONSTRAINT chk_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
                       CONSTRAINT chk_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_posts_user_created ON posts (user_id, created_at DESC);
CREATE INDEX idx_posts_created      ON posts (created_at DESC);

CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_updated_at
BEFORE UPDATE ON posts
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- Comments
-- ============================================================

CREATE TABLE comments (
                          id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          post_id      UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
                          comment_text TEXT NOT NULL,
                          created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT chk_comment_text_not_blank CHECK (
                              LENGTH(TRIM(comment_text)) > 0
                              )
);

CREATE INDEX idx_comments_post_created ON comments (post_id, created_at DESC);
CREATE INDEX idx_comments_user ON comments (user_id);

-- ============================================================
-- Likes
-- ============================================================

CREATE TABLE likes (
                       id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                       post_id    UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       UNIQUE (user_id, post_id)
);

CREATE INDEX idx_likes_post ON likes (post_id);

-- ============================================================
-- Friends
-- ============================================================

CREATE TABLE friends (
                         user_id_1   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         user_id_2   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         PRIMARY KEY (user_id_1, user_id_2),
                         CONSTRAINT chk_no_self_friendship CHECK (user_id_1 <> user_id_2),
                         CONSTRAINT chk_friend_pair_order CHECK (user_id_1 < user_id_2)
);

CREATE INDEX idx_friends_user_2 ON friends (user_id_2);

-- ============================================================
-- Friend requests
-- ============================================================

CREATE TABLE friend_requests (
                                 sender_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                 receiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                 created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (sender_id, receiver_id),
                                 CONSTRAINT chk_no_self_friend_request CHECK (sender_id <> receiver_id)
);

CREATE INDEX idx_friend_requests_receiver ON friend_requests (receiver_id);

-- ============================================================
-- User cars
-- ============================================================

CREATE TABLE users_cars (
                            id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            user_id       UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                            car_model_id  UUID NOT NULL REFERENCES car_models(id) ON DELETE RESTRICT,
                            image_path    TEXT NOT NULL,
                            created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER set_users_cars_updated_at
BEFORE UPDATE ON users_cars
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();