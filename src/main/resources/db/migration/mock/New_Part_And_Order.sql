-- ============================================================
-- APMS 测试数据生成脚本 (Oracle / DBeaver)
-- 6 customers (已有) -> 2,000 orders -> 20,000 parts
-- ============================================================
-- DBeaver 使用说明:
--   1. 打开 SQL 编辑器，粘贴整个脚本
--   2. 用 Alt+X (Execute Script) 而不是 Ctrl+Enter
--   3. 如果报分隔符错误，在编辑器左下角把 delimiter 改为 /
-- ============================================================
-- 清理（如需重跑，先执行这两行）:
--   DELETE FROM APMS_PART WHERE CREATED_BY = 'DATA_SEED';
--   DELETE FROM APMS_RETURN_ORDER WHERE CREATED_BY = 'DATA_SEED';
--   COMMIT;
-- ============================================================

DECLARE
    c_order_count    CONSTANT PLS_INTEGER := 2000;
    c_part_count     CONSTANT PLS_INTEGER := 20000;
    c_batch_size     CONSTANT PLS_INTEGER := 500;

    TYPE t_str_tab IS TABLE OF VARCHAR2(100) INDEX BY PLS_INTEGER;
    TYPE t_id_tab  IS TABLE OF VARCHAR2(36)  INDEX BY PLS_INTEGER;
    TYPE t_int_tab IS TABLE OF PLS_INTEGER   INDEX BY PLS_INTEGER;

    -- customer 数据（从已有表加载）
    v_cust_ids   t_id_tab;
    v_cust_names t_str_tab;
    v_cust_count PLS_INTEGER;

    -- order id 数组
    v_order_ids  t_id_tab;
    v_order_qty  t_int_tab;

    -- 枚举值
    v_return_methods  t_str_tab;
    v_complaint_types t_str_tab;
    v_statuses_order  t_str_tab;
    v_statuses_part   t_str_tab;
    v_bus_units       t_str_tab;
    v_platforms       t_str_tab;
    v_shifts          t_str_tab;
    v_bosch_failures  t_str_tab;
    v_cust_failures   t_str_tab;

    v_order_id   VARCHAR2(36);
    v_id         VARCHAR2(36);
    v_idx        PLS_INTEGER;
    v_cust_idx   PLS_INTEGER;
    v_comp_type  VARCHAR2(20);
    v_base_date  DATE := DATE '2023-01-01';

BEGIN
    -- ========================================================
    -- 0. 加载已有 customer
    -- ========================================================
    SELECT ID, NAME BULK COLLECT INTO v_cust_ids, v_cust_names
      FROM APMS_CUSTOMER
     WHERE ROWNUM <= 50
     ORDER BY CREATED_AT;

    v_cust_count := v_cust_ids.COUNT;

    IF v_cust_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'APMS_CUSTOMER 表为空，请先插入客户数据');
    END IF;

    DBMS_OUTPUT.PUT_LINE('Loaded ' || v_cust_count || ' customers.');

    -- ========================================================
    -- 枚举初始化
    -- ========================================================
    v_return_methods(1) := 'EXPRESS';
    v_return_methods(2) := 'SELF_PICKUP';
    v_return_methods(3) := 'LOGISTICS';

    -- BA20 = 0km，不可抽样
    v_complaint_types(1) := 'BA20';
    v_complaint_types(2) := 'FA10';
    v_complaint_types(3) := 'FA20';
    v_complaint_types(4) := 'FA30';

    v_statuses_order(1) := 'RECEIVED';
    v_statuses_order(2) := 'IN_PROGRESS';
    v_statuses_order(3) := 'COMPLETED';
    v_statuses_order(4) := 'CLOSED';

    v_statuses_part(1) := 'PENDING';
    v_statuses_part(2) := 'ANALYZING';
    v_statuses_part(3) := 'ANALYZED';
    v_statuses_part(4) := 'CLOSED';

    v_bus_units(1) := 'DS';
    v_bus_units(2) := 'PS';
    v_bus_units(3) := 'CM';
    v_bus_units(4) := 'XC';

    v_platforms(1) := 'MQB';
    v_platforms(2) := 'CMA';
    v_platforms(3) := 'TNGA';
    v_platforms(4) := 'MLB';
    v_platforms(5) := 'SEA';

    v_shifts(1) := 'DAY';
    v_shifts(2) := 'NIGHT';
    v_shifts(3) := 'MID';

    v_bosch_failures(1) := 'NTF';
    v_bosch_failures(2) := 'CDF';
    v_bosch_failures(3) := 'EOS';
    v_bosch_failures(4) := 'DOA';

    v_cust_failures(1) := 'ELECTRICAL';
    v_cust_failures(2) := 'MECHANICAL';
    v_cust_failures(3) := 'SOFTWARE';
    v_cust_failures(4) := 'UNKNOWN';

    -- ========================================================
    -- 1. 生成 2000 条 RETURN_ORDER
    -- ========================================================
    DBMS_OUTPUT.PUT_LINE('Inserting orders...');

    FOR i IN 1..c_order_count LOOP
        v_order_id := RAWTOHEX(SYS_GUID());
        v_order_ids(i) := v_order_id;
        v_order_qty(i) := 0;
        v_cust_idx := MOD(i - 1, v_cust_count) + 1;
        v_comp_type := v_complaint_types(MOD(i - 1, 4) + 1);

        INSERT INTO APMS_RETURN_ORDER (
            ID, ORDER_NUMBER, CUSTOMER, CUSTOMER_ID,
            RECEIVE_DATE, COMPLAINT_DATE, RETURN_METHOD,
            TRACKING_NUMBER, RETURN_QUANTITY,
            STATUS, COMPLAINT_TYPE,
            CREATED_BY, CREATED_AT
        ) VALUES (
            v_order_id,
            'RO' || LPAD(TO_CHAR(i), 6, '0'),
            v_cust_names(v_cust_idx),
            v_cust_ids(v_cust_idx),
            v_base_date + MOD(i * 7, 800),
            v_base_date + MOD(i * 7, 800) - TRUNC(DBMS_RANDOM.VALUE(1, 30)),
            v_return_methods(MOD(i - 1, 3) + 1),
            'SF' || LPAD(TO_CHAR(TRUNC(DBMS_RANDOM.VALUE(100000000, 999999999))), 10, '0'),
            0,
            v_statuses_order(MOD(i - 1, 4) + 1),
            v_comp_type,
            'DATA_SEED',
            SYSTIMESTAMP
        );

        IF MOD(i, c_batch_size) = 0 THEN
            COMMIT;
            DBMS_OUTPUT.PUT_LINE('  orders: ' || i || ' / ' || c_order_count);
        END IF;
    END LOOP;
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Orders done.');

    -- ========================================================
    -- 2. 生成 20000 条 PART（轮询分配，每 order 约 10 条）
    -- ========================================================
    DBMS_OUTPUT.PUT_LINE('Inserting parts...');

    FOR i IN 1..c_part_count LOOP
        v_id  := RAWTOHEX(SYS_GUID());
        v_idx := MOD(i - 1, c_order_count) + 1;
        v_comp_type := v_complaint_types(MOD(i - 1, 4) + 1);

        INSERT INTO APMS_PART (
            ID, PART_NUMBER, ORDER_ID, PART_CODE,
            BUSINESS_UNIT, PRODUCT_PLATFORM, PRODUCTION_SHIFT,
            COMPLAINT_TYPE,
            VEHICLE_PRODUCTION_DATE, VEHICLE_PURCHASE_DATE,
            VEHICLE_FAILURE_DATE, VEHICLE_VIN, VEHICLE_MILEAGE,
            CUSTOMER_DESCRIPTION, REPAIR_STATION,
            RESPONSIBLE_ENGINEER, ANALYST,
            QC_NO, IS_SAMPLE,
            STATUS, CUSTOMER_FAILURE_TYPE, BOSCH_FAILURE_TYPE,
            CREATED_BY, CREATED_AT
        ) VALUES (
            v_id,
            'P' || LPAD(TO_CHAR(i), 7, '0'),
            v_order_ids(v_idx),
            'PC' || LPAD(TO_CHAR(MOD(i - 1, 500) + 1), 4, '0'),
            v_bus_units(MOD(i - 1, 4) + 1),
            v_platforms(MOD(i - 1, 5) + 1),
            v_shifts(MOD(i - 1, 3) + 1),
            v_comp_type,
            v_base_date + MOD(i * 3, 700) - 180,
            v_base_date + MOD(i * 3, 700) - 90,
            v_base_date + MOD(i * 3, 700),
            SUBSTR(DBMS_RANDOM.STRING('X', 17), 1, 17),
            TRUNC(DBMS_RANDOM.VALUE(100, 200000)),
            'Customer complaint #' || TO_CHAR(i),
            'Station-' || TO_CHAR(MOD(i - 1, 20) + 1),
            'Engineer-' || TO_CHAR(MOD(i - 1, 15) + 1),
            'Analyst-' || TO_CHAR(MOD(i - 1, 10) + 1),
            'QC' || LPAD(TO_CHAR(MOD(i - 1, 1000)), 5, '0'),
            CASE WHEN v_comp_type = 'BA20' THEN 0
                 ELSE MOD(i, 2)
            END,
            v_statuses_part(MOD(i - 1, 4) + 1),
            v_cust_failures(MOD(i - 1, 4) + 1),
            v_bosch_failures(MOD(i - 1, 4) + 1),
            'DATA_SEED',
            SYSTIMESTAMP
        );

        v_order_qty(v_idx) := v_order_qty(v_idx) + 1;

        IF MOD(i, c_batch_size) = 0 THEN
            COMMIT;
            DBMS_OUTPUT.PUT_LINE('  parts: ' || i || ' / ' || c_part_count);
        END IF;
    END LOOP;
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Parts done.');

    -- ========================================================
    -- 3. 回写 RETURN_QUANTITY
    -- ========================================================
    DBMS_OUTPUT.PUT_LINE('Updating order quantities...');

    FOR i IN 1..c_order_count LOOP
        UPDATE APMS_RETURN_ORDER
           SET RETURN_QUANTITY = v_order_qty(i),
               UPDATED_BY     = 'DATA_SEED',
               UPDATED_AT     = SYSTIMESTAMP
         WHERE ID = v_order_ids(i);

        IF MOD(i, c_batch_size) = 0 THEN
            COMMIT;
        END IF;
    END LOOP;
    COMMIT;

    DBMS_OUTPUT.PUT_LINE('=== DONE: ' || c_order_count || ' orders, ' || c_part_count || ' parts ===');
END;
/