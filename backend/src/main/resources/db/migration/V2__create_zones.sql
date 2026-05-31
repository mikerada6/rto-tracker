CREATE TABLE zones (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id),
    name          VARCHAR(255) NOT NULL,
    type          VARCHAR(50) NOT NULL,
    external_id   VARCHAR(255),
    latitude      DECIMAL(9,6),
    longitude     DECIMAL(9,6),
    radius_meters INTEGER,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_zones_user_external UNIQUE (user_id, external_id)
);

CREATE INDEX idx_zones_user_id ON zones(user_id);
