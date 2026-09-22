CREATE TABLE tourism_sync_runs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scope_key VARCHAR(80) NOT NULL,
    l_dong_regn_cd VARCHAR(10) NOT NULL,
    l_dong_signgu_cd VARCHAR(10) NOT NULL,
    content_type_id VARCHAR(10) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    status VARCHAR(20) NOT NULL,
    remote_call_count INT NOT NULL DEFAULT 0,
    fetched_count INT NOT NULL DEFAULT 0,
    inserted_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    unchanged_count INT NOT NULL DEFAULT 0,
    deactivated_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    failure_category VARCHAR(50) NULL,
    CONSTRAINT ck_tourism_sync_runs_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_tourism_sync_runs_counts CHECK (
        remote_call_count >= 0 AND fetched_count >= 0 AND inserted_count >= 0
        AND updated_count >= 0 AND unchanged_count >= 0 AND deactivated_count >= 0 AND failed_count >= 0
    ),
    INDEX ix_tourism_sync_runs_scope_started (scope_key, started_at),
    INDEX ix_tourism_sync_runs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tourism_sync_scope_states (
    scope_key VARCHAR(80) NOT NULL PRIMARY KEY,
    l_dong_regn_cd VARCHAR(10) NOT NULL,
    l_dong_signgu_cd VARCHAR(10) NOT NULL,
    content_type_id VARCHAR(10) NOT NULL,
    last_successful_sync_at DATETIME(6) NOT NULL,
    last_successful_run_id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_tourism_sync_scope_last_run
        FOREIGN KEY (last_successful_run_id) REFERENCES tourism_sync_runs(id) ON DELETE RESTRICT,
    CONSTRAINT uk_tourism_sync_scope_dimensions UNIQUE (l_dong_regn_cd, l_dong_signgu_cd, content_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
