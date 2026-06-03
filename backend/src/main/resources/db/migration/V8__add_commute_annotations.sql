ALTER TABLE users
    ADD COLUMN commute_anomaly_threshold_minutes INT NOT NULL DEFAULT 45;

CREATE TABLE commute_annotations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id),
    date          DATE NOT NULL,
    start_time    TIMESTAMPTZ NOT NULL,
    end_time      TIMESTAMPTZ NOT NULL,
    category      VARCHAR(32) NOT NULL,
    note          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_commute_annotation_window CHECK (end_time > start_time)
);

CREATE INDEX idx_commute_annotations_user_date ON commute_annotations(user_id, date);
