-- ============================================================
-- Car families
-- Groups catalog models (e.g. all Golf variants) under a single stable
-- target so a challenge can reference "Volkswagen Golf" instead of one
-- car_models row. Scoped to a brand: a family cannot mix models from two
-- different brands.
-- ============================================================

CREATE TABLE car_families (
                              id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              brand      VARCHAR(50) NOT NULL,
                              name       VARCHAR(80) NOT NULL,
                              created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

                              UNIQUE (brand, name)
);

-- Auxiliary unique index so car_models can carry a composite FK (brand, family_id)
-- that guarantees a model can only be linked to a family of the same brand.
CREATE UNIQUE INDEX idx_car_families_brand_id ON car_families (brand, id);

ALTER TABLE car_models
    ADD COLUMN family_id UUID;

ALTER TABLE car_models
    ADD CONSTRAINT fk_car_models_family
        FOREIGN KEY (brand, family_id)
        REFERENCES car_families (brand, id)
        ON DELETE SET NULL (family_id);

CREATE INDEX idx_car_models_family ON car_models (family_id);
