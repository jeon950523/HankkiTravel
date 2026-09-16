-- Preserve the V4 foundation and legacy rows; never invent dates/owners for old drafts.
ALTER TABLE trips ADD COLUMN public_id CHAR(36) NULL;
ALTER TABLE trips ADD COLUMN guest_id BIGINT NULL;
ALTER TABLE trips ADD COLUMN region_key VARCHAR(20) NULL;
ALTER TABLE trips ADD COLUMN start_date DATE NULL;
ALTER TABLE trips ADD COLUMN end_date DATE NULL;
ALTER TABLE trips ADD CONSTRAINT uk_trips_public_id UNIQUE (public_id);
ALTER TABLE trips ADD CONSTRAINT fk_trips_guest FOREIGN KEY (guest_id) REFERENCES guests(id) ON DELETE RESTRICT;
ALTER TABLE trips ADD CONSTRAINT ck_trips_schedule CHECK (
    (public_id IS NULL AND guest_id IS NULL AND region_key IS NULL AND start_date IS NULL AND end_date IS NULL)
    OR (public_id IS NOT NULL AND guest_id IS NOT NULL AND region_key IS NOT NULL
        AND start_date IS NOT NULL AND end_date IS NOT NULL
        AND region_key IN ('JEJU', 'GYEONGJU') AND end_date >= start_date
        AND TIMESTAMPDIFF(DAY, start_date, end_date) <= 3)
);
CREATE INDEX ix_trips_guest_created ON trips (guest_id, created_at);

CREATE TABLE trip_days (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    day_number INT NOT NULL,
    travel_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_trip_days_number CHECK (day_number BETWEEN 1 AND 4),
    CONSTRAINT uk_trip_days_number UNIQUE (trip_id, day_number),
    CONSTRAINT uk_trip_days_date UNIQUE (trip_id, travel_date),
    CONSTRAINT fk_trip_days_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_meal_slots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    trip_day_id BIGINT NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_trip_slots_public UNIQUE (public_id),
    CONSTRAINT uk_trip_slots_meal UNIQUE (trip_day_id, meal_type),
    CONSTRAINT ck_trip_slots_meal CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER')),
    CONSTRAINT fk_trip_slots_day FOREIGN KEY (trip_day_id) REFERENCES trip_days(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meal_anchors (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_meal_slot_id BIGINT NOT NULL,
    provider VARCHAR(10) NOT NULL,
    content_id VARCHAR(20) NOT NULL,
    content_type VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_meal_anchors_slot UNIQUE (trip_meal_slot_id),
    CONSTRAINT ck_meal_anchors_provider CHECK (provider = 'KTO'),
    CONSTRAINT ck_meal_anchors_content CHECK (CHAR_LENGTH(TRIM(content_id)) > 0),
    CONSTRAINT fk_meal_anchors_slot FOREIGN KEY (trip_meal_slot_id) REFERENCES trip_meal_slots(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
