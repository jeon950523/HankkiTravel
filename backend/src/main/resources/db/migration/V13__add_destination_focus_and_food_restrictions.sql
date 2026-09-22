ALTER TABLE trip_day_place_anchors DROP CONSTRAINT ck_trip_day_place_anchor_slot;
ALTER TABLE trip_day_place_anchors ADD CONSTRAINT ck_trip_day_place_anchor_slot
    CHECK (slot_type IN ('DAY_FOCUS','MORNING_ACTIVITY','AFTERNOON_ACTIVITY','STAY'));

CREATE TABLE family_member_food_restrictions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    family_member_id BIGINT NOT NULL,
    restriction_type VARCHAR(20) NOT NULL,
    normalized_value VARCHAR(100) NOT NULL,
    display_value VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_family_member_food_restrictions UNIQUE (family_member_id, restriction_type, normalized_value),
    CONSTRAINT ck_family_member_food_restrictions_type CHECK (restriction_type IN ('ALLERGEN','AVOID')),
    CONSTRAINT fk_family_member_food_restrictions_member FOREIGN KEY (family_member_id)
        REFERENCES family_members(id) ON DELETE CASCADE,
    INDEX ix_family_member_food_restrictions_member (family_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
