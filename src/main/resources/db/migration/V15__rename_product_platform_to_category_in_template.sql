-- V15: Rename PRODUCT_PLATFORM to PRODUCT_CATEGORY in APMS_REPORT_TEMPLATE
-- Date: 2026-03-16
-- Description: Change template matching from product platform to product category
-- Requirements:
--   - Template matching now uses productCategory + failureType instead of productPlatform + failureType
--   - Part entity keeps productPlatform field for display, but template matching uses productCategory

-- Step 1: Add new column PRODUCT_CATEGORY
ALTER TABLE APMS_REPORT_TEMPLATE ADD PRODUCT_CATEGORY VARCHAR2(50 CHAR);

-- Step 2: Copy data from PRODUCT_PLATFORM to PRODUCT_CATEGORY
UPDATE APMS_REPORT_TEMPLATE SET PRODUCT_CATEGORY = PRODUCT_PLATFORM WHERE PRODUCT_PLATFORM IS NOT NULL;

-- Step 3: Drop old index on PRODUCT_PLATFORM
DROP INDEX IDX_REPORT_TEMPLATE_PLATFORM;

-- Step 4: Drop old column PRODUCT_PLATFORM
ALTER TABLE APMS_REPORT_TEMPLATE DROP COLUMN PRODUCT_PLATFORM;

-- Step 5: Create new index on PRODUCT_CATEGORY
CREATE INDEX IDX_REPORT_TEMPLATE_CATEGORY ON APMS_REPORT_TEMPLATE(PRODUCT_CATEGORY);

-- Note: IDX_REPORT_TEMPLATE_FAILURE index remains unchanged as FAILURE_TYPE is still used
