CREATE TABLE office_day_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    date                DATE NOT NULL,
    total_office_time   BIGINT,
    commute_duration    BIGINT,
    first_office_entry  TIMESTAMPTZ,
    last_office_exit    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_office_day_user_date UNIQUE (user_id, date)
);

CREATE INDEX idx_office_day_records_user_date ON office_day_records(user_id, date);

CREATE TABLE office_day_offices_visited (
    office_day_record_id UUID NOT NULL REFERENCES office_day_records(id) ON DELETE CASCADE,
    zone_id              UUID NOT NULL REFERENCES zones(id),
    PRIMARY KEY (office_day_record_id, zone_id)
);
