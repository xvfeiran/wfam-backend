-- 同一退货单下退件编号唯一（允许不同退货单使用相同编号）
ALTER TABLE APMS_PART ADD CONSTRAINT uk_order_part_number UNIQUE (ORDER_ID, PART_NUMBER);
