CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_users_status CHECK (CHAR_LENGTH(TRIM(status)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE guests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_guests_public_id UNIQUE (public_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE family_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NULL,
    owner_guest_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_profiles_one_owner CHECK (
        (owner_user_id IS NOT NULL AND owner_guest_id IS NULL) OR
        (owner_user_id IS NULL AND owner_guest_id IS NOT NULL)
    ),
    CONSTRAINT ck_profiles_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT fk_profiles_user FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_profiles_guest FOREIGN KEY (owner_guest_id) REFERENCES guests(id) ON DELETE RESTRICT,
    INDEX ix_profiles_user (owner_user_id),
    INDEX ix_profiles_guest (owner_guest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE family_members (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_members_nickname CHECK (CHAR_LENGTH(TRIM(nickname)) > 0),
    CONSTRAINT ck_members_sort CHECK (sort_order >= 0),
    CONSTRAINT fk_members_profile FOREIGN KEY (profile_id) REFERENCES family_profiles(id) ON DELETE RESTRICT,
    INDEX ix_members_profile_order (profile_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
