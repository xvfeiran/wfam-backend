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
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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

    public void generateReport(String reportId, OutputStream outputStream) throws IOException {
        AnalysisReport report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        ReportTemplate template = templateRepository.findById(report.getTemplateId())
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + report.getTemplateId()));

        File templateFile = new File(template.getFilePath());
        if (!templateFile.exists()) {
            throw new IllegalArgumentException("Template file not found: " + template.getFilePath());
        }

        try (Workbook workbook = WorkbookFactory.create(templateFile)) {
            Map<String, Object> content = parseJsonContent(report.getContent());
            fillDataToWorkbook(workbook, content);
            workbook.write(outputStream);
            log.info("Generated report for reportId: {}", reportId);
        }
    }

    private Map<String, Object> parseJsonContent(String jsonContent) {
        try {
            return objectMapper.readValue(jsonContent, new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Failed to parse report content JSON", e);
            return Map.of();
        }
    }

    private void fillDataToWorkbook(Workbook workbook, Map<String, Object> data) {
        Pattern pattern = Pattern.compile("\\[\\[([a-zA-Z0-9_]+)\\]\\]");

        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    String cellValue = getCellValueAsString(cell);
                    if (cellValue != null && cellValue.contains("[[")) {
                        Matcher matcher = pattern.matcher(cellValue);
                        StringBuffer result = new StringBuffer();
                        while (matcher.find()) {
                            String fieldName = matcher.group(1);
                            Object value = data.get(fieldName);
                            String replacement = value != null ? value.toString() : "";
                            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                        }
                        matcher.appendTail(result);
                        setCellValue(cell, result.toString());
                    }
                }
            }
        }
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
