package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateFieldDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ExcelTemplateParserService {

    private static final Pattern PLACEHOLDER_PATTERN =
        Pattern.compile("\\[\\[([a-z]+):([^:]+):([^:]*):([^:]*):([^:]*):([^\\]]*)\\]\\]");

    public List<ReportTemplateFieldDTO> parseTemplate(MultipartFile file) throws IOException {
        List<ReportTemplateFieldDTO> fields = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            for (Sheet sheet : workbook) {
                parseSheet(sheet, fields);
            }
        }
        log.info("Parsed template file: {}, found {} fields", file.getOriginalFilename(), fields.size());
        return fields;
    }

    private void parseSheet(Sheet sheet, List<ReportTemplateFieldDTO> fields) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String cellValue = getCellValueAsString(cell);
                if (cellValue != null && cellValue.contains("[[")) {
                    extractPlaceholders(cellValue, fields);
                }
            }
        }
    }

    private void extractPlaceholders(String text, List<ReportTemplateFieldDTO> fields) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            String labelZh = matcher.group(3).isEmpty() ? name : matcher.group(3);
            String labelEn = matcher.group(4).isEmpty() ? name : matcher.group(4);
            boolean required = matcher.group(5).isEmpty() ? false : Boolean.parseBoolean(matcher.group(5));
            String optionsStr = matcher.group(6);

            List<String> options = null;
            if ("select".equals(type) && !optionsStr.isEmpty()) {
                options = Arrays.asList(optionsStr.split(","));
            }

            if (fields.stream().noneMatch(f -> f.getName().equals(name))) {
                fields.add(ReportTemplateFieldDTO.builder()
                    .name(name)
                    .type(type)
                    .label(labelZh)
                    .required(required)
                    .options(options)
                    .build());
                log.debug("Extracted field: name={}, type={}, label={}", name, type, labelZh);
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
                long longValue = (long) cell.getNumericCellValue();
                yield String.valueOf(longValue);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }
}
