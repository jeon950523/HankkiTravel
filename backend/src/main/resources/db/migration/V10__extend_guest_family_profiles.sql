ALTER TABLE family_profiles ADD COLUMN transport_mode VARCHAR(20) NOT NULL DEFAULT 'CAR';
ALTER TABLE family_profiles ADD COLUMN parking_preference VARCHAR(20) NOT NULL DEFAULT 'NO_PREFERENCE';
ALTER TABLE family_profiles ADD COLUMN walking_burden_preference VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE family_profiles ADD COLUMN transfer_preference VARCHAR(20) NOT NULL DEFAULT 'NO_PREFERENCE';
ALTER TABLE family_profiles ADD COLUMN stairs_avoidance BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE family_members ADD COLUMN continuous_walking_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE family_members ADD COLUMN stairs_preference VARCHAR(20) NOT NULL DEFAULT 'NEUTRAL';

CREATE TABLE family_member_cautions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    family_member_id BIGINT NOT NULL,
    caution VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_family_member_cautions UNIQUE (family_member_id, caution),
    CONSTRAINT ck_family_member_cautions_value CHECK (caution IN (
        'SODIUM', 'SUGAR', 'CARBOHYDRATE', 'SPICY', 'INGREDIENT_CHECK', 'NONE'
    )),
    CONSTRAINT fk_family_member_cautions_member FOREIGN KEY (family_member_id)
        REFERENCES family_members(id) ON DELETE CASCADE,
    INDEX ix_family_member_cautions_member (family_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
