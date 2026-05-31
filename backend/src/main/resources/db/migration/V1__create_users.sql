CREATE TABLE users (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                  VARCHAR(255) NOT NULL UNIQUE,
    display_name           VARCHAR(255) NOT NULL,
    api_key_hash           VARCHAR(255) NOT NULL UNIQUE,
    active                 BOOLEAN NOT NULL DEFAULT TRUE,
    required_days_per_week DECIMAL(3,1) NOT NULL DEFAULT 3.0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
