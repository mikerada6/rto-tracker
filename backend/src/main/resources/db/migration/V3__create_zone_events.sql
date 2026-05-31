CREATE TABLE zone_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    zone_id    UUID NOT NULL REFERENCES zones(id),
    event_type VARCHAR(10) NOT NULL,
    timestamp  TIMESTAMPTZ NOT NULL,
    latitude   DECIMAL(9,6),
    longitude  DECIMAL(9,6),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_zone_events_user_timestamp ON zone_events(user_id, timestamp);
CREATE INDEX idx_zone_events_zone_id        ON zone_events(zone_id);
