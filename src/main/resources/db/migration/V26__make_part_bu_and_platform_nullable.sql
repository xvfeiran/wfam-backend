-- V26: Make BUSINESS_UNIT and PRODUCT_PLATFORM nullable in APMS_PART
-- Date: 2026-04-13
-- Description: Align DB schema with UI/backend change where BU and product platform are optional

ALTER TABLE APMS_PART MODIFY (BUSINESS_UNIT NULL);
ALTER TABLE APMS_PART MODIFY (PRODUCT_PLATFORM NULL);
