-- V35__add_return_order_scrapped_status.sql
-- Return order status enum增加 'scrapped'（已报废）
-- Note: No table structure change needed, status field is VARCHAR2(50)
-- This script documents the status addition for reference

-- Add comment for documentation (Oracle doesn't execute this, it's for reference)
-- Status values: draft, submitted, scrapped
