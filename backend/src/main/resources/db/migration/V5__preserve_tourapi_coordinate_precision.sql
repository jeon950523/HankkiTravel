-- V3 has already been applied: widen forward without changing its checksum.
-- All 800 coordinate values in the four supplied TourAPI samples fit exactly.
ALTER TABLE tourism_places MODIFY COLUMN longitude DECIMAL(18,15) NULL;
ALTER TABLE tourism_places MODIFY COLUMN latitude DECIMAL(18,15) NULL;
