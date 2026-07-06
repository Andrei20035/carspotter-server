CREATE UNIQUE INDEX idx_users_phone_number
    ON users (phone_number)
    WHERE phone_number IS NOT NULL;
