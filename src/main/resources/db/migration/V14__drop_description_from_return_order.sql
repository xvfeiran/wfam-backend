-- Remove DESCRIPTION column from APMS_RETURN_ORDER table
-- NOTE: Flyway is disabled locally. Execute this SQL manually.
ALTER TABLE APMS_RETURN_ORDER DROP COLUMN DESCRIPTION;
