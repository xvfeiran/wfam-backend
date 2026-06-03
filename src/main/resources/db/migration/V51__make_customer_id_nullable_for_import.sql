-- V51: Make CUSTOMER_ID nullable to support import without customer information
-- This change allows import to work without requiring a valid customer ID,
-- while maintaining data integrity for manual operations

-- Remove the NOT NULL constraint from CUSTOMER_ID
ALTER TABLE APMS_RETURN_ORDER MODIFY CUSTOMER_ID VARCHAR2(36 CHAR) NULL;

-- Note: The foreign key constraint remains, so if a customer ID is provided,
-- it must still reference a valid customer in APMS_CUSTOMER table