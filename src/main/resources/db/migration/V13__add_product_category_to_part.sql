-- Add PRODUCT_CATEGORY column to APMS_PART table
-- NOTE: Flyway is disabled locally. Execute this SQL manually.
ALTER TABLE APMS_PART ADD PRODUCT_CATEGORY VARCHAR2(50 CHAR) NOT NULL;
