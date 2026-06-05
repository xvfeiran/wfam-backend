package com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.PartCode;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartCodeRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 解析 2025 售后件 Excel：
 * 第0行：文件信息
 * 第1行：中文表头
 * 第2行：英文表头
 * 第3行起：数据行
 *
 * 关键映射：
 * Excel第2列 博世零件号 -> partCode
 * Excel第4列(通常) 博世生产日期 -> partProductionDate（可选，按表头名称动态定位）
 * Excel第5列 班次 -> productionShift
 * Excel第6列 车辆生产日期 -> vehicleProductionDate
 * Excel第7列 失效日期 -> vehicleFailureDate
 * Excel第8列 维修站号/... -> repairStation
 * Excel第11列 VIN码/... -> vehicleVIN
 * Excel第12列 购车日期 -> vehiclePurchaseDate
 * Excel第13列 行驶里程 -> vehicleMileage
 * Excel第14列 客户失效描述 -> customerDescription
 * Excel第15列 故障模式 -> failureType
 * Excel第21列 其他 -> otherDescription
 * Excel第23列 博世失效描述 -> boschFailureType
 * Excel第32列 QC号 -> qcNo（仅12位纯数字时生效）
 * Excel第33列 退货单号 -> orderNumber
 * MRB号列（可选，Tesla专用） -> otherInfo，格式："MRB 号：{value}"
 *
 * 事业部/产品平台通过零件号主数据映射，不再直接读取 Excel 列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartImportParser {

    private static final int HEADER_ROW_CN_INDEX = 1;
    private static final int HEADER_ROW_EN_INDEX = 2;
    private static final int DATA_START_ROW_INDEX = 3;

    private static final List<String> ORDER_NUMBER_HEADERS = List.of("退货单号", "return form", "return order",
            "return no");
    private static final List<String> PART_CODE_HEADERS = List.of("博世零件号", "bosch p/n", "bosch part", "part code",
            "零件号");
    private static final List<String> PRODUCTION_SHIFT_HEADERS = List.of("班次", "shift");
    private static final List<String> VEHICLE_PRODUCTION_DATE_HEADERS = List.of("车辆生产日期", "vehicle prod date",
            "vehicle production date");
    private static final List<String> VEHICLE_FAILURE_DATE_HEADERS = List.of("失效日期", "failure date", "车辆故障日期",
            "vehicle failure date");
    private static final List<String> REPAIR_STATION_HEADERS = List.of("维修站号", "维修站", "maintain station",
            "repair station");
    private static final List<String> VEHICLE_VIN_HEADERS = List.of("vin码", "vin/chassis", "vin", "底盘号", "车架号");
    private static final List<String> VEHICLE_PURCHASE_DATE_HEADERS = List.of("购车日期", "vehicle purch",
            "vehicle purchase date");
    private static final List<String> VEHICLE_MILEAGE_HEADERS = List.of("行驶里程", "mileage");
    private static final List<String> CUSTOMER_DESCRIPTION_HEADERS = List.of("客户失效描述", "cuco description",
            "customer description");
    private static final List<String> FAILURE_TYPE_HEADERS = List.of("故障模式", "failure mode");
    private static final List<String> OTHER_DESCRIPTION_HEADERS = List.of("其他", "others");
    private static final List<String> BOSCH_FAILURE_TYPE_HEADERS = List.of("博世失效", "failure desc", "bosch failure");
    private static final List<String> QC_NO_HEADERS = List.of("qc号", "qc notification", "qc no");
    private static final List<String> MRB_NO_HEADERS = List.of("mrb号", "mrb no", "mrb number");
    private static final List<String> PART_PRODUCTION_DATE_HEADERS = List.of(
            "博世生产日期", "bosch prod", "供方生产日期", "supplier prod", "part prod");

    private static final String DEFAULT_IMPORTED_ANALYST = "-";

    private final PartCodeRepository partCodeRepository;
    private final DataFormatter dataFormatter = new DataFormatter();

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Getter
    public static class ParseResult {
        private final int rowNum; // 1-based display row number
        private final PartDTO dto; // non-null if success
        private final String error; // non-null if failure
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
        log.debug("[PartParser] 开始从字节数组解析，大小: {} bytes", bytes.length);
        return parseStream(new ByteArrayInputStream(bytes));
    }

    private List<ParseResult> parseStream(InputStream inputStream) throws IOException {
        List<ParseResult> results = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            log.debug("[PartParser] 工作表总行数: {}，数据起始行索引: {}", lastRow, DATA_START_ROW_INDEX);

            ColumnMapping mapping = resolveColumnMapping(sheet);
            for (int i = DATA_START_ROW_INDEX; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    log.debug("[PartParser] 第{}行为空，跳过", i + 1);
                    continue;
                }

                String partCodeRaw = getCellString(row, mapping.partCodeCol());
                if (partCodeRaw == null || partCodeRaw.isBlank()) {
                    log.debug("[PartParser] 第{}行零件号为空，按空行跳过", i + 1);
                    continue;
                }

                int displayRowNum = i + 1; // 1-based for user display
                Map<String, String> rawData = captureRawData(row, mapping);
                try {
                    PartDTO dto = parseRow(row, mapping);
                    log.debug("[PartParser] 第{}行解析成功: orderNumber={}, partCode={}",
                            displayRowNum, dto.getOrderNumber(), dto.getPartCode());
                    results.add(ParseResult.success(displayRowNum, dto, rawData));
                } catch (Exception e) {
                    log.debug("[PartParser] 第{}行解析失败: {}", displayRowNum, e.getMessage());
                    results.add(ParseResult.failure(displayRowNum, e.getMessage(), rawData));
                }
            }
        }

        log.debug("[PartParser] 解析完成，有效行数: {}", results.size());
        return results;
    }

    private PartDTO parseRow(Row row, ColumnMapping mapping) {
        String orderNumber = getCellString(row, mapping.orderNumberCol());
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("退货单号不能为空");
        }

        String partCode = getCellString(row, mapping.partCodeCol());
        if (partCode == null || partCode.isBlank()) {
            throw new IllegalArgumentException("博世零件号不能为空");
        }

        PartCode partCodeMapping = partCodeRepository.findByPartCode(partCode).orElse(null);

        String businessUnit = partCodeMapping == null ? null : normalizeText(partCodeMapping.getBusinessUnit());
        String productPlatform = partCodeMapping == null ? null : normalizeText(partCodeMapping.getProductPlatform());

        String mrbNo = normalizeText(getCellString(row, mapping.mrbNoCol()));
        String otherInfo = mrbNo != null ? "MRB 号：" + mrbNo : null;

        return PartDTO.builder()
                .orderNumber(orderNumber)
                .partCode(partCode)
                .businessUnit(businessUnit)
                .productPlatform(productPlatform)
                .productionShift(normalizeText(getCellString(row, mapping.productionShiftCol())))
                .failureType(normalizeText(getCellString(row, mapping.failureTypeCol())))
                .boschFailureType(normalizeText(getCellString(row, mapping.boschFailureTypeCol())))
                .vehicleProductionDate(parseDate(getCellString(row, mapping.vehicleProductionDateCol())))
                .vehiclePurchaseDate(parseDate(getCellString(row, mapping.vehiclePurchaseDateCol())))
                .vehicleFailureDate(parseDate(getCellString(row, mapping.vehicleFailureDateCol())))
                .vehicleVIN(normalizeText(getCellString(row, mapping.vehicleVinCol())))
                .vehicleMileage(parseInteger(getCellString(row, mapping.vehicleMileageCol())))
                .customerDescription(normalizeText(getCellString(row, mapping.customerDescriptionCol())))
                .otherDescription(normalizeText(getCellString(row, mapping.otherDescriptionCol())))
                .repairStation(normalizeText(getCellString(row, mapping.repairStationCol())))
                .analyst(DEFAULT_IMPORTED_ANALYST)
                .qcNo(normalizeQcNo(getCellString(row, mapping.qcNoCol())))
                .otherInfo(otherInfo)
                .partProductionDate(parseDate(getCellString(row, mapping.partProductionDateCol())))
                .build();
    }

    private String parseDate(String rawDate) {
        String trimmed = normalizeText(rawDate);
        if (trimmed == null)
            return null;

        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(trimmed, fmt);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new IllegalArgumentException("无法解析日期格式: " + rawDate);
    }

    private Integer parseInteger(String rawValue) {
        String normalized = normalizeText(rawValue);
        if (normalized == null)
            return null;
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            // For import compatibility, treat non-integer mileage as empty instead of
            // failing the row.
            return null;
        }
    }

    private String getCellString(Row row, int col) {
        if (row == null || col < 0)
            return null;
        Cell cell = row.getCell(col);
        if (cell == null)
            return null;

        return switch (cell.getCellType()) {
            case STRING -> {
                String val = cell.getStringCellValue().trim();
                yield val.isEmpty() ? null : val;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                }
                String value = dataFormatter.formatCellValue(cell);
                yield value == null || value.isBlank() ? null : value.trim();
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
                if (val != null && !val.isBlank())
                    return false;
            }
        }
        return true;
    }

    /** 抓取行中关键列的原始值，用于失败/成功日志 */
    private Map<String, String> captureRawData(Row row, ColumnMapping mapping) {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("orderNumber", getCellString(row, mapping.orderNumberCol()));
        raw.put("partCode", getCellString(row, mapping.partCodeCol()));
        raw.put("vehicleFailureDate", getCellString(row, mapping.vehicleFailureDateCol()));
        raw.put("repairStation", getCellString(row, mapping.repairStationCol()));
        raw.put("customerDescription", getCellString(row, mapping.customerDescriptionCol()));
        raw.put("qcNo", getCellString(row, mapping.qcNoCol()));
        return raw;
    }

    private ColumnMapping resolveColumnMapping(Sheet sheet) {
        Row cnHeader = sheet.getRow(HEADER_ROW_CN_INDEX);
        Row enHeader = sheet.getRow(HEADER_ROW_EN_INDEX);

        Map<Integer, String> headers = new HashMap<>();
        int maxCol = Math.max(
                cnHeader != null ? cnHeader.getLastCellNum() : 0,
                enHeader != null ? enHeader.getLastCellNum() : 0);

        for (int col = 0; col < maxCol; col++) {
            String cn = normalizeHeader(getCellString(cnHeader, col));
            String en = normalizeHeader(getCellString(enHeader, col));
            headers.put(col, (cn == null ? "" : cn) + "|" + (en == null ? "" : en));
        }

        return new ColumnMapping(
                resolveRequiredColumn(headers, ORDER_NUMBER_HEADERS, "退货单号"),
                resolveRequiredColumn(headers, PART_CODE_HEADERS, "博世零件号"),
                resolveOptionalColumn(headers, PRODUCTION_SHIFT_HEADERS),
                resolveOptionalColumn(headers, VEHICLE_PRODUCTION_DATE_HEADERS),
                resolveOptionalColumn(headers, VEHICLE_FAILURE_DATE_HEADERS),
                resolveOptionalColumn(headers, REPAIR_STATION_HEADERS),
                resolveOptionalColumn(headers, VEHICLE_VIN_HEADERS),
                resolveOptionalColumn(headers, VEHICLE_PURCHASE_DATE_HEADERS),
                resolveOptionalColumn(headers, VEHICLE_MILEAGE_HEADERS),
                resolveOptionalColumn(headers, CUSTOMER_DESCRIPTION_HEADERS),
                resolveOptionalColumn(headers, FAILURE_TYPE_HEADERS),
                resolveOptionalColumn(headers, OTHER_DESCRIPTION_HEADERS),
                resolveOptionalColumn(headers, BOSCH_FAILURE_TYPE_HEADERS),
                resolveOptionalColumn(headers, QC_NO_HEADERS),
                resolveOptionalColumn(headers, MRB_NO_HEADERS),
                resolveOptionalColumn(headers, PART_PRODUCTION_DATE_HEADERS));
    }

    private int resolveRequiredColumn(Map<Integer, String> headers, List<String> aliases, String displayName) {
        int col = resolveOptionalColumn(headers, aliases);
        if (col < 0) {
            throw new IllegalArgumentException("未找到表头列: " + displayName);
        }
        return col;
    }

    private int resolveOptionalColumn(Map<Integer, String> headers, List<String> aliases) {
        List<String> normalizedAliases = aliases.stream()
                .map(this::normalizeHeader)
                .filter(Objects::nonNull)
                .toList();

        for (Map.Entry<Integer, String> entry : headers.entrySet()) {
            for (String alias : normalizedAliases) {
                if (entry.getValue().contains(alias)) {
                    return entry.getKey();
                }
            }
        }

        return -1;
    }

    private String normalizeHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (Set.of("N/A", "NA", "NULL", "-").contains(upper)) {
            return null;
        }
        return trimmed;
    }

    private String normalizeQcNo(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.matches("\\d{12}") ? normalized : null;
    }

    private record ColumnMapping(
            int orderNumberCol,
            int partCodeCol,
            int productionShiftCol,
            int vehicleProductionDateCol,
            int vehicleFailureDateCol,
            int repairStationCol,
            int vehicleVinCol,
            int vehiclePurchaseDateCol,
            int vehicleMileageCol,
            int customerDescriptionCol,
            int failureTypeCol,
            int otherDescriptionCol,
            int boschFailureTypeCol,
            int qcNoCol,
            int mrbNoCol,
            int partProductionDateCol) {
    }
}
