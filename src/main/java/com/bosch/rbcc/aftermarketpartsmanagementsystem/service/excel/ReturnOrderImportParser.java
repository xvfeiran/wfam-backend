package com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析新格式退货单 Excel：
 *   第0行（行索引0）：标题行，忽略
 *   第1行（行索引1）：表头行，忽略
 *   第2行起（行索引2+）：数据行
 *
 * 列映射：
 *   列0  序号        忽略
 *   列1  收货时间    receiveDate + complaintDate
 *   列2  快递公司    忽略
 *   列3  快递单号    trackingNumber
 *   列4  收件人      忽略
 *   列5  寄件地址    忽略
 *   列6  0Km/Field   忽略（固定 BA40）
 *   列7  数量        忽略（固定 0）
 *   列8  退货单号    忽略
 *   列9  备注        description
 *
 * 固定字段：
 *   customerId   = 60399b05-b5e9-4b0e-b2eb-107df4ebbcbc (UNKNOWN)
 *   returnMethod = express
 *   returnQuantity = 0
 *   failureType  = BA40
 */
@Slf4j
@Component
public class ReturnOrderImportParser {

    private static final String FIXED_CUSTOMER_ID = "60399b05-b5e9-4b0e-b2eb-107df4ebbcbc";
    private static final String FIXED_RETURN_METHOD = "express";
    private static final int FIXED_RETURN_QUANTITY = 0;
    private static final String FIXED_FAILURE_TYPE = "BA40";

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    @Getter
    public static class ParseResult {
        private final int rowNum;              // 1-based display row number
        private final ReturnOrderDTO dto;      // non-null if success
        private final String error;            // non-null if failure
        private final Map<String, String> rawData; // raw cell values captured from the row

        private ParseResult(int rowNum, ReturnOrderDTO dto, String error, Map<String, String> rawData) {
            this.rowNum = rowNum;
            this.dto = dto;
            this.error = error;
            this.rawData = rawData;
        }

        public static ParseResult success(int rowNum, ReturnOrderDTO dto, Map<String, String> rawData) {
            return new ParseResult(rowNum, dto, null, rawData);
        }

        public static ParseResult failure(int rowNum, String error, Map<String, String> rawData) {
            return new ParseResult(rowNum, null, error, rawData);
        }

        public boolean isSuccess() {
            return dto != null;
        }
    }

    /** 从 MultipartFile 解析（同步上传时使用） */
    public List<ParseResult> parse(MultipartFile file) throws IOException {
        log.info("[Parser] 开始解析文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());
        return parseStream(file.getInputStream());
    }

    /** 从 byte[] 解析（异步任务中使用，避免请求结束后流失效） */
    public List<ParseResult> parseBytes(byte[] bytes) throws IOException {
        log.info("[Parser] 开始从字节数组解析，大小: {} bytes", bytes.length);
        return parseStream(new ByteArrayInputStream(bytes));
    }

    private List<ParseResult> parseStream(InputStream inputStream) throws IOException {
        List<ParseResult> results = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            log.info("[Parser] 工作表总行数: {}，数据起始行索引: 2", lastRow);

            // Data starts at row index 2 (skip title row 0 and header row 1)
            for (int i = 2; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    log.debug("[Parser] 第{}行为空，跳过", i + 1);
                    continue;
                }

                int displayRowNum = i + 1; // 1-based for user display
                Map<String, String> rawData = captureRawData(row);
                try {
                    ReturnOrderDTO dto = parseRow(row);
                    log.debug("[Parser] 第{}行解析成功: receiveDate={}, trackingNumber={}",
                            displayRowNum, dto.getReceiveDate(), dto.getTrackingNumber());
                    results.add(ParseResult.success(displayRowNum, dto, rawData));
                } catch (Exception e) {
                    log.warn("[Parser] 第{}行解析失败: {}", displayRowNum, e.getMessage());
                    results.add(ParseResult.failure(displayRowNum, e.getMessage(), rawData));
                }
            }
        }

        log.info("[Parser] 解析完成，有效行数: {}", results.size());
        return results;
    }

    private ReturnOrderDTO parseRow(Row row) {
        // Column 1: receive date (also used as complaint date)
        String dateStr = getCellString(row, 1);
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException("收货时间不能为空");
        }
        String parsedDate = parseDate(dateStr);

        // Column 3: tracking number (optional)
        String trackingNumber = getCellString(row, 3);

        // Column 9: description (optional)
        String description = getCellString(row, 9);

        return ReturnOrderDTO.builder()
                .customerId(FIXED_CUSTOMER_ID)
                .receiveDate(parsedDate)
                .complaintDate(parsedDate)
                .returnMethod(FIXED_RETURN_METHOD)
                .trackingNumber(trackingNumber)
                .returnQuantity(FIXED_RETURN_QUANTITY)
                .failureType(FIXED_FAILURE_TYPE)
                .description(description)
                .build();
    }

    private String parseDate(String rawDate) {
        String trimmed = rawDate.trim();

        // Try known string formats
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(trimmed, fmt);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }

        throw new IllegalArgumentException("无法解析日期格式: " + rawDate);
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> {
                String val = cell.getStringCellValue().trim();
                yield val.isEmpty() ? null : val;
            }
            case NUMERIC -> {
                // If the cell is formatted as a date, return ISO date string
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                }
                yield NumberToTextConverter.toText(cell.getNumericCellValue());
            }
            case BLANK -> null;
            default -> null;
        };
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellString(row, c);
                if (val != null && !val.isBlank()) return false;
            }
        }
        return true;
    }

    /** 抓取行中关键列的原始值，用于失败/成功日志 */
    private Map<String, String> captureRawData(Row row) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("收货时间", getCellString(row, 1));
        raw.put("快递单号", getCellString(row, 3));
        raw.put("备注",    getCellString(row, 9));
        return raw;
    }
}
