package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisReport;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReportTemplate;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisReportRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReportTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExcelReportGeneratorService {

    private final AnalysisReportRepository reportRepository;
    private final ReportTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;

    public void generateReport(String reportId, OutputStream outputStream) throws IOException {
        AnalysisReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        ReportTemplate template = templateRepository.findById(report.getTemplateId())
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + report.getTemplateId()));

        // filePath 格式为 "templates/WSA-NVH-xxx.xlsx"，需拆分为 category + 文件名
        String filePath = template.getFilePath();
        int sep = filePath.indexOf('/');
        String category = sep >= 0 ? filePath.substring(0, sep) : "templates";
        String fileName = sep >= 0 ? filePath.substring(sep + 1) : filePath;

        Resource templateResource = fileStorageService.load(category, fileName);
        if (templateResource == null) {
            throw new IllegalArgumentException("Template file not found on SMB: " + filePath);
        }

        try (InputStream is = templateResource.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Map<String, Object> content = parseJsonContent(report.getContent());
            log.info("Generating report: reportId={}, contentKeys={}", reportId, content.keySet());
            fillDataToWorkbook(workbook, content);
            workbook.write(outputStream);
            log.info("Generated report for reportId: {}", reportId);
        }
    }

    private Map<String, Object> parseJsonContent(String jsonContent) {
        if (jsonContent == null || jsonContent.isBlank()) {
            log.warn("Report content is null or empty, returning empty map");
            return Map.of();
        }
        try {
            return objectMapper.readValue(jsonContent, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse report content JSON: {}", jsonContent, e);
            return Map.of();
        }
    }

    private void fillDataToWorkbook(Workbook workbook, Map<String, Object> data) {
        // 占位符格式：[[type:fieldName:labelZh:labelEn:required:options]]
        // fieldName 使用 [^:]+ 与 ExcelTemplateParserService 保持一致，
        // 支持中文、连字符等非 ASCII 字段名；仅不能包含冒号。
        Pattern pattern = Pattern.compile("\\[\\[[a-z]+:([^:]+):[^\\]]*\\]\\]");
        int replaced = 0;

        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    String cellValue = getCellValueAsString(cell);
                    if (cellValue != null && cellValue.contains("[[")) {
                        Matcher matcher = pattern.matcher(cellValue);
                        StringBuffer result = new StringBuffer();
                        boolean anyMatch = false;
                        while (matcher.find()) {
                            anyMatch = true;
                            String fieldName = matcher.group(1);
                            Object value = data.get(fieldName);
                            log.debug("Replace placeholder: field='{}', value={}", fieldName, value);
                            String replacement = value != null ? value.toString() : "";
                            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                            replaced++;
                        }
                        if (!anyMatch) {
                            log.warn("No placeholder pattern matched in cell: {}", cellValue);
                        }
                        matcher.appendTail(result);
                        setCellValue(cell, result.toString());
                    }
                }
            }
        }
        log.info("fillDataToWorkbook: total replacements={}", replaced);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                yield String.valueOf(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private void setCellValue(Cell cell, String value) {
        if (value == null || value.isEmpty()) {
            cell.setBlank();
            return;
        }

        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle style = cell.getCellStyle();

        try {
            if (style != null && style.getDataFormatString() != null
                && style.getDataFormatString().contains("%")) {
                double numValue = Double.parseDouble(value.replace("%", "").trim());
                cell.setCellValue(numValue / 100);
            } else {
                double numValue = Double.parseDouble(value);
                cell.setCellValue(numValue);
            }
        } catch (NumberFormatException e) {
            cell.setCellValue(value);
        }
    }
}
