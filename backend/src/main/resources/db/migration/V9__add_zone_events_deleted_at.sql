ALTER TABLE zone_events ADD COLUMN deleted_at TIMESTAMPTZ NULL;

CREATE INDEX idx_zone_events_active
    ON zone_events (user_id, timestamp)
    WHERE deleted_at IS NULL;
