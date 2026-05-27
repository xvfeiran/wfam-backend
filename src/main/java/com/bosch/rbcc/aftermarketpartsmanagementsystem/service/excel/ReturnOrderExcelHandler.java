package com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReturnOrderExcelHandler {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 40 columns in exact spec order
    private static final String[] HEADERS = {
            // 退件单 (11)
            "退货单号", "客户", "收货日期", "投诉日期", "退货方式", "物流单号",
            "退货数量", "投诉类型", "退货单状态", "退货单创建人", "退货单创建时间",
            // 零件信息 (13)
            "退件编号", "零件代码(FIS)", "事业群", "产品平台", "生产班次",
            "客户故障类型", "博世故障类型", "零件状态", "是否取样",
            "QC编号", "责任工程师", "分析师", "投诉位置",
            // 车辆信息 (5)
            "车辆生产日期", "车辆购买日期", "车辆故障日期", "VIN", "里程(km)",
            // 描述 (3)
            "客户描述", "其他描述", "维修站",
            // 审计 (4)
            "零件创建人", "零件创建时间", "零件更新人", "零件更新时间",
            // 汇总 (4)
            "初始分析数量", "精细分析数量", "报废数量", "QC已创建数量"
    };

    private static final Map<String, String> ORDER_STATUS_MAP = Map.of(
            "draft", "草稿", "submitted", "已提交", "scrapped", "已报废"
    );

    private static final Map<String, String> RETURN_METHOD_MAP = Map.of(
            "express", "快递", "pickup", "自提", "other", "其他"
    );

    private static final Map<String, String> FAILURE_TYPE_MAP = Map.of(
            "NVH", "NVH", "APPEARANCE", "外观", "FUNCTION", "功能"
    );

    /**
     * Exports a flat list of order+part rows to Excel.
     */
    public byte[] exportToExcel(List<ExportRow> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("退件明细");
            CellStyle headerStyle = createHeaderStyle(wb);
            createHeaderRow(sheet, headerStyle);
            fillDataRows(sheet, rows);
            autoSizeColumns(sheet);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ExcelOperationException("Excel export failed: " + e.getMessage(), e);
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void createHeaderRow(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void fillDataRows(Sheet sheet, List<ExportRow> rows) {
        int rowIdx = 1;
        for (ExportRow row : rows) {
            Row excelRow = sheet.createRow(rowIdx++);
            fillExportRow(excelRow, row);
        }
    }

    private void fillExportRow(Row r, ExportRow d) {
        // 退件单 (0-10)
        r.createCell(0).setCellValue(s(d.order().getOrderNumber()));
        r.createCell(1).setCellValue(s(d.order().getCustomer()));
        r.createCell(2).setCellValue(s(d.order().getReceiveDate()));
        r.createCell(3).setCellValue(s(d.order().getComplaintDate()));
        r.createCell(4).setCellValue(map(RETURN_METHOD_MAP, d.order().getReturnMethod()));
        r.createCell(5).setCellValue(s(d.order().getTrackingNumber()));
        setIntCell(r, 6, d.order().getReturnQuantity());
        r.createCell(7).setCellValue(s(d.order().getComplaintType()));
        r.createCell(8).setCellValue(map(ORDER_STATUS_MAP, d.order().getStatus()));
        r.createCell(9).setCellValue(s(d.order().getCreatedBy()));
        r.createCell(10).setCellValue(formatDateTime(d.order().getCreatedAt()));

        // 零件信息 (11-23)
        r.createCell(11).setCellValue(s(d.part().getPartNumber()));
        r.createCell(12).setCellValue(s(d.part().getPartCode()));
        r.createCell(13).setCellValue(s(d.part().getBusinessUnit()));
        r.createCell(14).setCellValue(s(d.part().getProductPlatform()));
        r.createCell(15).setCellValue(s(d.part().getProductionShift()));
        r.createCell(16).setCellValue(map(FAILURE_TYPE_MAP, d.part().getFailureType()));
        r.createCell(17).setCellValue(s(d.part().getBoschFailureType()));
        r.createCell(18).setCellValue(s(d.part().getStatus()));
        r.createCell(19).setCellValue(d.part().getIsSample() != null && d.part().getIsSample() == 1 ? "是" : "否");
        r.createCell(20).setCellValue(s(d.part().getQcNo()));
        r.createCell(21).setCellValue(s(d.part().getResponsibleEngineer()));
        r.createCell(22).setCellValue(s(d.part().getAnalyst()));
        r.createCell(23).setCellValue(s(d.part().getComplaintLocation()));

        // 车辆信息 (24-28)
        r.createCell(24).setCellValue(s(d.part().getVehicleProductionDate()));
        r.createCell(25).setCellValue(s(d.part().getVehiclePurchaseDate()));
        r.createCell(26).setCellValue(s(d.part().getVehicleFailureDate()));
        r.createCell(27).setCellValue(s(d.part().getVehicleVIN()));
        setIntCell(r, 28, d.part().getVehicleMileage());

        // 描述 (29-31)
        r.createCell(29).setCellValue(s(d.part().getCustomerDescription()));
        r.createCell(30).setCellValue(s(d.part().getOtherDescription()));
        r.createCell(31).setCellValue(s(d.part().getRepairStation()));

        // 审计 (32-35)
        r.createCell(32).setCellValue(s(d.part().getCreatedBy()));
        r.createCell(33).setCellValue(formatDateTime(d.part().getCreatedAt()));
        r.createCell(34).setCellValue(s(d.part().getUpdatedBy()));
        r.createCell(35).setCellValue(formatDateTime(d.part().getUpdatedAt()));

        // 汇总 (36-39)
        setIntCell(r, 36, d.order().getInitialAnalysisQuantity());
        setIntCell(r, 37, d.order().getDetailedAnalysisQuantity());
        setIntCell(r, 38, d.order().getScrappedQuantity());
        setIntCell(r, 39, d.order().getQcCreatedQuantity());
    }

    private static void setIntCell(Row r, int col, Integer val) {
        if (val != null) {
            r.createCell(col).setCellValue(val);
        }
    }

    private static String s(String val) {
        return val != null ? val : "";
    }

    private static String map(Map<String, String> m, String key) {
        if (key == null) return "";
        return m.getOrDefault(key, key);
    }

    private static String formatDateTime(String val) {
        if (val == null || val.isBlank()) return "";
        try {
            if (val.length() == 10) return val;
            return LocalDateTime.parse(val, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(DATETIME_FMT);
        } catch (Exception e) {
            return val;
        }
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // --- Import methods (unchanged) ---

    public List<ReturnOrderDTO> importFromExcel(MultipartFile file) {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            return parseOrdersFromSheet(sheet);
        } catch (IOException e) {
            throw new ExcelOperationException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }

    private List<ReturnOrderDTO> parseOrdersFromSheet(Sheet sheet) {
        List<ReturnOrderDTO> orders = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            try {
                orders.add(parseOrderFromRow(row));
            } catch (Exception e) {
                // Skip invalid rows
            }
        }
        return orders;
    }

    private ReturnOrderDTO parseOrderFromRow(Row row) {
        return ReturnOrderDTO.builder()
                .customer(getCellString(row, 0))
                .receiveDate(getCellString(row, 1))
                .complaintDate(getCellString(row, 2))
                .returnMethod(getCellString(row, 3))
                .trackingNumber(getCellString(row, 4))
                .returnQuantity((int) row.getCell(5).getNumericCellValue())
                .build();
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    public record ExportRow(ReturnOrderDTO order, PartDTO part) {}

    public static class ExcelOperationException extends RuntimeException {
        public ExcelOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
