package com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PartExcelHandler {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] HEADERS = {
            // 基本信息
            "退货单号", "退件编号", "零件代码(FIS)", "事业群", "产品平台",
            // 生产信息
            "生产班次", "生产日期",
            // 故障信息
            "客户故障类型", "博世故障类型", "投诉位置",
            // 状态
            "零件状态", "是否取样", "QC编号",
            // 人员
            "责任工程师", "分析师",
            // 车辆信息
            "车辆生产日期", "车辆购买日期", "车辆故障日期", "VIN", "里程(km)",
            // 描述
            "客户描述", "其他描述", "维修站",
            // 审计
            "创建人", "创建时间", "更新人", "更新时间",
    };

    private static final Map<String, String> PART_STATUS_MAP = Map.of(
            "draft", "草稿",
            "submitted", "已提交",
            "in_initial_analysis", "初始分析中",
            "initial_analysis_completed", "初始分析完成",
            "in_detailed_analysis", "精细分析中",
            "pending_approval", "待审批",
            "analysis_completed", "分析完成",
            "analysis_skipped", "分析跳过",
            "scrap_in_progress", "报废中",
            "scrapped", "已报废"
    );

    private static final Map<String, String> FAILURE_TYPE_MAP = Map.of(
            "NVH", "NVH", "APPEARANCE", "外观", "FUNCTION", "功能"
    );

    public byte[] exportToExcel(List<PartDTO> parts) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("售后件明细");
            CellStyle headerStyle = createHeaderStyle(wb);
            createHeaderRow(sheet, headerStyle);
            fillDataRows(sheet, parts);
            autoSizeColumns(sheet);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReturnOrderExcelHandler.ExcelOperationException("Excel export failed: " + e.getMessage(), e);
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

    private void fillDataRows(Sheet sheet, List<PartDTO> parts) {
        int rowIdx = 1;
        for (PartDTO p : parts) {
            Row r = sheet.createRow(rowIdx++);
            int col = 0;
            // 基本信息
            r.createCell(col++).setCellValue(s(p.getOrderNumber()));
            r.createCell(col++).setCellValue(s(p.getPartNumber()));
            r.createCell(col++).setCellValue(s(p.getPartCode()));
            r.createCell(col++).setCellValue(s(p.getBusinessUnit()));
            r.createCell(col++).setCellValue(s(p.getProductPlatform()));
            // 生产信息
            r.createCell(col++).setCellValue(s(p.getProductionShift()));
            r.createCell(col++).setCellValue(s(p.getPartProductionDate()));
            // 故障信息
            r.createCell(col++).setCellValue(map(FAILURE_TYPE_MAP, p.getFailureType()));
            r.createCell(col++).setCellValue(s(p.getBoschFailureType()));
            r.createCell(col++).setCellValue(s(p.getComplaintLocation()));
            // 状态
            r.createCell(col++).setCellValue(map(PART_STATUS_MAP, p.getStatus()));
            r.createCell(col++).setCellValue(p.getIsSample() != null && p.getIsSample() == 1 ? "是" : "否");
            r.createCell(col++).setCellValue(s(p.getQcNo()));
            // 人员
            r.createCell(col++).setCellValue(s(p.getResponsibleEngineer()));
            r.createCell(col++).setCellValue(s(p.getAnalyst()));
            // 车辆信息
            r.createCell(col++).setCellValue(s(p.getVehicleProductionDate()));
            r.createCell(col++).setCellValue(s(p.getVehiclePurchaseDate()));
            r.createCell(col++).setCellValue(s(p.getVehicleFailureDate()));
            r.createCell(col++).setCellValue(s(p.getVehicleVIN()));
            setIntCell(r, col++, p.getVehicleMileage());
            // 描述
            r.createCell(col++).setCellValue(s(p.getCustomerDescription()));
            r.createCell(col++).setCellValue(s(p.getOtherDescription()));
            r.createCell(col++).setCellValue(s(p.getRepairStation()));
            // 审计
            r.createCell(col++).setCellValue(s(p.getCreatedBy()));
            r.createCell(col++).setCellValue(formatDateTime(p.getCreatedAt()));
            r.createCell(col++).setCellValue(s(p.getUpdatedBy()));
            r.createCell(col++).setCellValue(formatDateTime(p.getUpdatedAt()));
        }
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static void setIntCell(Row r, int col, Integer val) {
        if (val != null) r.createCell(col).setCellValue(val);
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
}
