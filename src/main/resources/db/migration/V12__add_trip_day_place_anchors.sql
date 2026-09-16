CREATE TABLE trip_day_place_anchors (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    trip_day_id BIGINT NOT NULL,
    slot_type VARCHAR(30) NOT NULL,
    provider VARCHAR(10) NOT NULL,
    content_id VARCHAR(20) NOT NULL,
    content_type VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_trip_day_place_anchor_public UNIQUE (public_id),
    CONSTRAINT uk_trip_day_place_anchor_slot UNIQUE (trip_day_id, slot_type),
    CONSTRAINT ck_trip_day_place_anchor_slot CHECK (slot_type IN ('MORNING_ACTIVITY','AFTERNOON_ACTIVITY','STAY')),
    CONSTRAINT ck_trip_day_place_anchor_provider CHECK (provider = 'KTO'),
    CONSTRAINT ck_trip_day_place_anchor_content CHECK (CHAR_LENGTH(TRIM(content_id)) > 0),
    CONSTRAINT fk_trip_day_place_anchor_day FOREIGN KEY (trip_day_id) REFERENCES trip_days(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
