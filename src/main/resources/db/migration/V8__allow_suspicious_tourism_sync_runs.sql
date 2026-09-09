ALTER TABLE tourism_sync_runs DROP CONSTRAINT ck_tourism_sync_runs_status;

ALTER TABLE tourism_sync_runs ADD CONSTRAINT ck_tourism_sync_runs_status CHECK (
    status IN ('RUNNING', 'SUCCESS', 'SUSPICIOUS', 'FAILED')
);
