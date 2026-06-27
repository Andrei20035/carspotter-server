-- Pas 1: Early Spotter feature
-- Userii existenti raman cu is_early_spotter=false si early_spotter_number=null (DEFAULT / nullable).
-- Counter-ul incepe de la 0, independent de numarul de useri existenti.

ALTER TABLE users
    ADD COLUMN is_early_spotter     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN early_spotter_number INTEGER;

ALTER TABLE users
    ADD CONSTRAINT chk_early_spotter_number_range
    CHECK (early_spotter_number IS NULL OR early_spotter_number BETWEEN 1 AND 1000);

ALTER TABLE users
    ADD CONSTRAINT chk_early_spotter_consistency
    CHECK (
        (is_early_spotter = TRUE  AND early_spotter_number IS NOT NULL) OR
        (is_early_spotter = FALSE AND early_spotter_number IS NULL)
    );

CREATE UNIQUE INDEX uq_users_early_spotter_number
    ON users (early_spotter_number)
    WHERE early_spotter_number IS NOT NULL;

CREATE TABLE early_spotter_counter (
    id            SMALLINT PRIMARY KEY DEFAULT 1,
    last_assigned INTEGER  NOT NULL DEFAULT 0,
    CONSTRAINT chk_single_row          CHECK (id = 1),
    CONSTRAINT chk_last_assigned_range CHECK (last_assigned BETWEEN 0 AND 1000)
);

INSERT INTO early_spotter_counter (id, last_assigned) VALUES (1, 0);
