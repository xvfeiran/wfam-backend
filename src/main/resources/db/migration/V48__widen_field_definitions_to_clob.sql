-- V48: Widen FIELD_DEFINITIONS from VARCHAR2(4000) to CLOB
-- Date: 2026-05-26
-- Reason: Large templates (photo/photolist fields, 40+ fields) exceed the 4000-byte Oracle VARCHAR2 limit.
--         CLOB supports up to 4 GB and is the standard choice for variable-length JSON storage.
--
-- Oracle allows converting VARCHAR2 → CLOB only when the column has no data, OR via a temp-column approach.
-- The safest portable approach that handles existing data:
--   1. Add a temp CLOB column
--   2. Copy existing data
--   3. Drop the old VARCHAR2 column
--   4. Rename temp column to the original name

-- Step 1: add temporary CLOB column
ALTER TABLE APMS_REPORT_TEMPLATE ADD FIELD_DEFINITIONS_CLB CLOB;

-- Step 2: copy existing VARCHAR2 data into CLOB column
UPDATE APMS_REPORT_TEMPLATE
   SET FIELD_DEFINITIONS_CLB = TO_CLOB(FIELD_DEFINITIONS)
 WHERE FIELD_DEFINITIONS IS NOT NULL;

-- Step 3: drop old VARCHAR2 column
ALTER TABLE APMS_REPORT_TEMPLATE DROP COLUMN FIELD_DEFINITIONS;

-- Step 4: rename CLOB column to original name
ALTER TABLE APMS_REPORT_TEMPLATE RENAME COLUMN FIELD_DEFINITIONS_CLB TO FIELD_DEFINITIONS;
