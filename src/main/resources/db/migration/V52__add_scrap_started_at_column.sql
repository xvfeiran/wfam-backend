-- V53: Add missing SCRAP_STARTED_AT column to APMS_ANALYSIS_ORDER
-- 添加报废开始时间字段，用于跟踪分析订单的报废时间

-- 对比分析：
-- Java AnalysisOrder实体字段 vs 数据库表字段
-- ✅ ID - 两者都有
-- ✅ ORDER_ID - 两者都有
-- ✅ ANALYST - 两者都有
-- ✅ STATUS - 两者都有
-- ✅ WORKON_SCRAP_NO - 两者都有（V50已添加）
-- ❌ SCRAP_STARTED_AT - Java有，数据库缺失！
-- ✅ STATUS_CHANGED_AT - 两者都有
-- ✅ CREATED_BY - 两者都有
-- ✅ CREATED_AT - 两者都有
-- ✅ UPDATED_BY - 两者都有
-- ✅ UPDATED_AT - 两者都有

-- 检查并添加 SCRAP_STARTED_AT 字段
DECLARE
  v_column_count NUMBER;
BEGIN
  -- 检查字段是否已存在
  SELECT COUNT(*) INTO v_column_count
  FROM user_tab_columns
  WHERE table_name = 'APMS_ANALYSIS_ORDER'
  AND column_name = 'SCRAP_STARTED_AT';

  -- 如果字段不存在，则添加
  IF v_column_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE APMS_ANALYSIS_ORDER ADD SCRAP_STARTED_AT TIMESTAMP (6)';
    DBMS_OUTPUT.PUT_LINE('已添加SCRAP_STARTED_AT字段到APMS_ANALYSIS_ORDER表');
  ELSE
    DBMS_OUTPUT.PUT_LINE('SCRAP_STARTED_AT字段已存在，跳过添加');
  END IF;

EXCEPTION
  WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('添加SCRAP_STARTED_AT字段失败: ' || SQLERRM);
    RAISE;
END;
/