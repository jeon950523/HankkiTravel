-- Nutrition schema is intentionally deferred until P2.1 source-data design.
CREATE TABLE trips (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_trips_status CHECK (CHAR_LENGTH(TRIM(status)) > 0),
    CONSTRAINT fk_trips_profile FOREIGN KEY (profile_id) REFERENCES family_profiles(id) ON DELETE RESTRICT,
    INDEX ix_trips_profile_status (profile_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
