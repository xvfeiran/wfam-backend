-- V54: Widen APMS_ANALYSIS_REPORT.CONTENT from VARCHAR2(4000 CHAR) to CLOB
-- Date: 2026-07-13
-- Reason: 分析报告内容（富文本）可超过 4000 字符，触发 ORA-12899 value too large for column CONTENT。
--         CLOB 支持到 4 GB，是变长长文本的标准选择。
--         参考 V48 的做法（FIELD_DEFINITIONS 转 CLOB）。
--
-- Oracle 仅在列无数据或借助临时列时才能完成 VARCHAR2 → CLOB 转换。
-- 兼容已有数据的安全做法：
--   1. 新增临时 CLOB 列
--   2. 拷贝已有数据
--   3. 删除旧的 VARCHAR2 列
--   4. 将临时列重命名为原列名

-- Step 1: add temporary CLOB column
ALTER TABLE APMS_ANALYSIS_REPORT ADD CONTENT_CLB CLOB;

-- Step 2: copy existing VARCHAR2 data into CLOB column
UPDATE APMS_ANALYSIS_REPORT
   SET CONTENT_CLB = TO_CLOB(CONTENT)
 WHERE CONTENT IS NOT NULL;

-- Step 3: drop old VARCHAR2 column
ALTER TABLE APMS_ANALYSIS_REPORT DROP COLUMN CONTENT;

-- Step 4: rename CLOB column to original name
ALTER TABLE APMS_ANALYSIS_REPORT RENAME COLUMN CONTENT_CLB TO CONTENT;
