-- Keep V1~V5 immutable. TourAPI's 14-digit source time has no documented timezone,
-- so its original representation is stored separately from the legacy DATETIME field.
ALTER TABLE tourism_places
    ADD COLUMN addr1 VARCHAR(500) NULL;
ALTER TABLE tourism_places
    ADD COLUMN addr2 VARCHAR(500) NULL;
ALTER TABLE tourism_places
    ADD COLUMN tel VARCHAR(100) NULL;
ALTER TABLE tourism_places
    ADD COLUMN zipcode VARCHAR(20) NULL;
ALTER TABLE tourism_places
    ADD COLUMN first_image VARCHAR(1000) NULL;
ALTER TABLE tourism_places
    ADD COLUMN first_image2 VARCHAR(1000) NULL;
ALTER TABLE tourism_places
    ADD COLUMN cpyrht_div_cd VARCHAR(30) NULL;
ALTER TABLE tourism_places
    ADD COLUMN lcls_systm1 VARCHAR(30) NULL;
ALTER TABLE tourism_places
    ADD COLUMN lcls_systm2 VARCHAR(30) NULL;
ALTER TABLE tourism_places
    ADD COLUMN lcls_systm3 VARCHAR(30) NULL;
ALTER TABLE tourism_places
    ADD COLUMN source_created_raw CHAR(14) NULL;
ALTER TABLE tourism_places
    ADD COLUMN source_modified_raw CHAR(14) NULL;
CREATE INDEX ix_tourism_source_modified_raw ON tourism_places (source_modified_raw);
