ALTER TABLE users
    ADD COLUMN full_name_changed_at TIMESTAMPTZ,
    ADD COLUMN country_changed_at TIMESTAMPTZ,
    ADD COLUMN birth_date_changed_at TIMESTAMPTZ,
    ADD COLUMN username_changed_at TIMESTAMPTZ,
    ADD COLUMN phone_number_changed_at TIMESTAMPTZ;
