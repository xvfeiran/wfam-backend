-- V10: Modify customer and references
-- 1. Make customer code nullable
-- 2. Add customer_id to return orders
-- 3. Migrate data from customer name to customer ID
-- 4. Add foreign key constraint

-- 1. 将客户代码改为可空（使用PL/SQL块处理，如果已经是NULL则跳过）
BEGIN
  EXECUTE IMMEDIATE
    'DECLARE
       v_count NUMBER;
     BEGIN
       SELECT COUNT(*) INTO v_count
       FROM user_tab_columns
       WHERE table_name = ''APMS_CUSTOMER''
       AND column_name = ''CODE''
       AND nullable = ''N'';
       IF v_count > 0 THEN
         EXECUTE IMMEDIATE ''ALTER TABLE APMS_CUSTOMER MODIFY CODE VARCHAR2(50 CHAR) NULL'';
       END IF;
     END;';
END;
/

-- 2. 添加客户ID字段到退货单表
ALTER TABLE APMS_RETURN_ORDER ADD CUSTOMER_ID VARCHAR2(36 CHAR);

-- 3. 数据迁移：通过客户名称关联客户ID
UPDATE APMS_RETURN_ORDER ro
SET CUSTOMER_ID = (SELECT ID FROM APMS_CUSTOMER WHERE NAME = ro.CUSTOMER)
WHERE CUSTOMER IN (SELECT NAME FROM APMS_CUSTOMER);

-- 4. 添加外键约束
ALTER TABLE APMS_RETURN_ORDER ADD CONSTRAINT FK_RETURN_ORDER_CUSTOMER
  FOREIGN KEY (CUSTOMER_ID) REFERENCES APMS_CUSTOMER(ID);

-- 5. 将客户ID设为必填（在数据迁移完成后）
-- 注意：如果有退货单的客户名称不在客户表中，此步骤会失败
-- 可以先执行以下查询检查是否有孤立数据：
-- SELECT CUSTOMER FROM APMS_RETURN_ORDER WHERE CUSTOMER NOT IN (SELECT NAME FROM APMS_CUSTOMER);
ALTER TABLE APMS_RETURN_ORDER MODIFY CUSTOMER_ID VARCHAR2(36 CHAR) NOT NULL;

-- 6. 将原客户名称字段设为可空，用于历史查询和显示
ALTER TABLE APMS_RETURN_ORDER MODIFY CUSTOMER VARCHAR2(100 CHAR) NULL;
