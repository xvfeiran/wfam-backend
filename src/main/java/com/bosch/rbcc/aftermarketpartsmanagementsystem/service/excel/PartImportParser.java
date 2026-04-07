package com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析售后件 Excel：
 *   第0行（行索引0）：标题行，忽略
 *   第1行（行索引1）：表头行，忽略
 *   第2行起（行索引2+）：数据行
 *
 * 列映射：
 *   列0  行号        忽略
 *   列1  退货单号    orderNumber（必填，用于查找 orderId）
 *   列2  零件代码    partCode（必填）
 *   列3  事业部      businessUnit（必填）
 *   列4  产品平台    productPlatform（必填）
 *   列5  生产班次    productionShift
 *   列6  故障类型    failureType
 *   列7  博世故障类型 boschFailureType
 *   列8  车辆生产日期 vehicleProductionDate
 *   列9  车辆购买日期 vehiclePurchaseDate
 *   列10 车辆故障日期 vehicleFailureDate
 *   列11 车辆VIN码   vehicleVin
 *   列12 车辆里程    vehicleMileage
 *   列13 客户描述    customerDescription
 *   列14 其他描述    otherDescription
 *   列15 维修站      repairStation
 *   列16 投诉地点    complaintLocation
 *   列17 责任工程师  responsibleEngineer
 *   列18 分析师      analyst（必填）
 *   列19 QC单号      qcNo
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartImportParser {

    private final ReturnOrderRepository returnOrderRepository;

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
        private final PartDTO dto;             // non-null if success
        private final String error;            // non-null if failure
        private final Map<String, String> rawData; // raw cell values captured from the row

        private ParseResult(int rowNum, PartDTO dto, String error, Map<String, String> rawData) {
            this.rowNum = rowNum;
            this.dto = dto;
            this.error = error;
            this.rawData = rawData;
        }

        public static ParseResult success(int rowNum, PartDTO dto, Map<String, String> rawData) {
            return new ParseResult(rowNum, dto, null, rawData);
        }

        public static ParseResult failure(int rowNum, String error, Map<String, String> rawData) {
            return new ParseResult(rowNum, null, error, rawData);
        }

        public boolean isSuccess() {
            return dto != null;
        }
    }

    /** 从 byte[] 解析（异步任务中使用，避免请求结束后流失效） */
    public List<ParseResult> parseBytes(byte[] bytes) throws IOException {
        log.info("[PartParser] 开始从字节数组解析，大小: {} bytes", bytes.length);
        return parseStream(new ByteArrayInputStream(bytes));
    }

    private List<ParseResult> parseStream(InputStream inputStream) throws IOException {
        List<ParseResult> results = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            log.info("[PartParser] 工作表总行数: {}，数据起始行索引: 2", lastRow);

            // Data starts at row index 2 (skip title row 0 and header row 1)
            for (int i = 2; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    log.debug("[PartParser] 第{}行为空，跳过", i + 1);
                    continue;
                }

                int displayRowNum = i + 1; // 1-based for user display
                Map<String, String> rawData = captureRawData(row);
                try {
                    PartDTO dto = parseRow(row);
                    log.debug("[PartParser] 第{}行解析成功: orderNumber={}, partCode={}",
                            displayRowNum, dto.getOrderNumber(), dto.getPartCode());
                    results.add(ParseResult.success(displayRowNum, dto, rawData));
                } catch (Exception e) {
                    log.warn("[PartParser] 第{}行解析失败: {}", displayRowNum, e.getMessage());
                    results.add(ParseResult.failure(displayRowNum, e.getMessage(), rawData));
                }
            }
        }

        log.info("[PartParser] 解析完成，有效行数: {}", results.size());
        return results;
    }

    private PartDTO parseRow(Row row) {
        // Column 1: 退货单号（必填）
        String orderNumber = getCellString(row, 1);
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("退货单号不能为空");
        }

        // 查找退货单获取 orderId
        ReturnOrder order = returnOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("退货单号不存在: " + orderNumber));

        // Column 2: 零件代码（必填）
        String partCode = getCellString(row, 2);
        if (partCode == null || partCode.isBlank()) {
            throw new IllegalArgumentException("零件代码不能为空");
        }

        // Column 3: 事业部（必填）
        String businessUnit = getCellString(row, 3);
        if (businessUnit == null || businessUnit.isBlank()) {
            throw new IllegalArgumentException("事业部不能为空");
        }

        // Column 4: 产品平台（必填）
        String productPlatform = getCellString(row, 4);
        if (productPlatform == null || productPlatform.isBlank()) {
            throw new IllegalArgumentException("产品平台不能为空");
        }

        // Column 18: 分析师（必填）
        String analyst = getCellString(row, 18);
        if (analyst == null || analyst.isBlank()) {
            throw new IllegalArgumentException("分析师不能为空");
        }

        // 构建 DTO
        return PartDTO.builder()
                .orderId(order.getId())
                .orderNumber(orderNumber)
                .partCode(partCode)
                .businessUnit(businessUnit)
                .productPlatform(productPlatform)
                .productionShift(getCellString(row, 5))
                .failureType(getCellString(row, 6))
                .boschFailureType(getCellString(row, 7))
                .vehicleProductionDate(parseDate(getCellString(row, 8)))
                .vehiclePurchaseDate(parseDate(getCellString(row, 9)))
                .vehicleFailureDate(parseDate(getCellString(row, 10)))
                .vehicleVIN(getCellString(row, 11))
                .vehicleMileage(parseInteger(getCellString(row, 12)))
                .customerDescription(getCellString(row, 13))
                .otherDescription(getCellString(row, 14))
                .repairStation(getCellString(row, 15))
                .complaintLocation(getCellString(row, 16))
                .responsibleEngineer(getCellString(row, 17))
                .analyst(analyst)
                .qcNo(getCellString(row, 19))
                .build();
    }

    private String parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return null;
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

    private Integer parseInteger(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析数字: " + rawValue);
        }
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
        raw.put("退货单号", getCellString(row, 1));
        raw.put("零件代码", getCellString(row, 2));
        raw.put("事业部", getCellString(row, 3));
        raw.put("产品平台", getCellString(row, 4));
        raw.put("分析师", getCellString(row, 18));
        return raw;
    }
}
