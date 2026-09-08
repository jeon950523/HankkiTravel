CREATE TABLE tourism_places (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content_id VARCHAR(32) NOT NULL,
    content_type_id VARCHAR(10) NOT NULL,
    title VARCHAR(300) NOT NULL,
    l_dong_regn_cd VARCHAR(10) NULL,
    l_dong_signgu_cd VARCHAR(10) NULL,
    longitude DECIMAL(13,10) NULL,
    latitude DECIMAL(13,10) NULL,
    source_modified_at DATETIME(6) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_tourism_content_id UNIQUE (content_id),
    CONSTRAINT ck_tourism_content_id CHECK (CHAR_LENGTH(TRIM(content_id)) > 0),
    CONSTRAINT ck_tourism_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_tourism_coordinates CHECK (
        (longitude IS NULL AND latitude IS NULL) OR
        (longitude IS NOT NULL AND latitude IS NOT NULL AND
         longitude BETWEEN -180 AND 180 AND latitude BETWEEN -90 AND 90)
    ),
    CONSTRAINT ck_tourism_active CHECK (active IN (TRUE, FALSE)),
    INDEX ix_tourism_region_type (l_dong_regn_cd, l_dong_signgu_cd, content_type_id),
    INDEX ix_tourism_source_modified (source_modified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE restaurants (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tourism_place_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_restaurants_place UNIQUE (tourism_place_id),
    CONSTRAINT fk_restaurants_place FOREIGN KEY (tourism_place_id) REFERENCES tourism_places(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
