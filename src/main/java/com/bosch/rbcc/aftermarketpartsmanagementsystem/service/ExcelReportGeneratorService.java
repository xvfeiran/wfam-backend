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
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.Units;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;

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
            fillDataToWorkbook(workbook, content);

            // 验证1：内存中 workbook 是否还有残留占位符
            int remaining = countRemainingPlaceholders(workbook);
            log.info("Verification (mem): remaining placeholders after fill = {}", remaining);

            // 先写到临时 buffer，验证写入后的字节
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            byte[] writtenBytes = baos.toByteArray();

            // 验证2：从写入的字节重新读取，检查是否还有占位符
            try (Workbook reRead = WorkbookFactory.create(new ByteArrayInputStream(writtenBytes))) {
                int remainingAfterWrite = countRemainingPlaceholders(reRead);
                log.info("Verification (written): remaining placeholders after write = {}", remainingAfterWrite);
            }

            outputStream.write(writtenBytes);
        }
    }

    private int countRemainingPlaceholders(Workbook workbook) {
        Pattern placeholderPattern = Pattern.compile("\\[\\[.*?\\]\\]");
        int count = 0;
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            Sheet sheet = workbook.getSheetAt(si);
            String sheetName = sheet.getSheetName();
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) continue;
                    String cellValue = getCellValueAsString(cell);
                    if (cellValue != null && placeholderPattern.matcher(cellValue).find()) {
                        log.warn("Remaining placeholder in sheet='{}', row={}, col={}: {}",
                            sheetName, row.getRowNum(), cell.getColumnIndex(), cellValue);
                        count++;
                    }
                }
            }
        }
        return count;
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

    private record ImagePlacement(Sheet sheet, int row, int col, List<String> paths) {}

    private void fillDataToWorkbook(Workbook workbook, Map<String, Object> data) {
        // 占位符格式：[[type:fieldName:labelZh:labelEn:required:options]]
        // 捕获 type(group1) + fieldName(group2)，fieldName 用 [^:]+ 支持中文字段名
        Pattern pattern = Pattern.compile("\\[\\[([a-z]+):([^:]+):[^\\]]*\\]\\]");
        int replaced = 0;
        int cellsWithPlaceholders = 0;
        List<ImagePlacement> images = new ArrayList<>();
        int sheetCount = workbook.getNumberOfSheets();

        for (int si = 0; si < sheetCount; si++) {
            Sheet sheet = workbook.getSheetAt(si);
            int firstRow = sheet.getFirstRowNum();
            int lastRow = sheet.getLastRowNum();
            for (int r = firstRow; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                int firstCol = row.getFirstCellNum();
                int lastCol = row.getLastCellNum();
                for (int c = firstCol; c < lastCol; c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) continue;
                    String cellValue = getCellValueAsString(cell);
                    if (cellValue != null && cellValue.contains("[[")) {
                        cellsWithPlaceholders++;
                        Matcher matcher = pattern.matcher(cellValue);
                        StringBuffer result = new StringBuffer();
                        boolean anyMatch = false;
                        while (matcher.find()) {
                            anyMatch = true;
                            String type = matcher.group(1);
                            String fieldName = matcher.group(2);
                            Object value = data.get(fieldName);

                            if ("photo".equals(type) || "photolist".equals(type)) {
                                // 照片字段：暂不作文本替换，收集路径后续嵌入图片
                                List<String> paths = extractImagePaths(value);
                                if (!paths.isEmpty()) {
                                    images.add(new ImagePlacement(sheet, r, c, paths));
                                }
                                matcher.appendReplacement(result, "");
                            } else {
                                String replacement = value != null ? value.toString() : "";
                                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                            }
                            replaced++;
                        }
                        if (!anyMatch) {
                            log.warn("No placeholder pattern matched in cell: {}", cellValue);
                        }
                        matcher.appendTail(result);
                        replaceCellValue(cell, result.toString().trim().isEmpty() ? null : result.toString().trim());
                    }
                }
            }
        }
        log.info("fillDataToWorkbook: {} cells processed, {} text replacements, {} image cells",
            cellsWithPlaceholders, replaced, images.size());

        // 清除主流程未匹配到的残留占位符（非必填字段未填时可能遗留）
        cleanupRemainingPlaceholders(workbook);

        // 嵌入图片
        for (ImagePlacement ip : images) {
            embedImages(workbook, ip);
        }
    }

    private List<String> extractImagePaths(Object value) {
        if (value == null) return List.of();
        if (value instanceof String s) {
            return s.isEmpty() ? List.of() : List.of(s);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(s -> !s.isEmpty())
                .toList();
        }
        return List.of();
    }

    private void cleanupRemainingPlaceholders(Workbook workbook) {
        Pattern placeholderPattern = Pattern.compile("\\[\\[[^\\]]*\\]\\]");
        int cleaned = 0;
        for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
            Sheet sheet = workbook.getSheetAt(si);
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) continue;
                    String cellValue = getCellValueAsString(cell);
                    if (cellValue != null && cellValue.contains("[[")) {
                        String cleanedValue = placeholderPattern.matcher(cellValue).replaceAll("").trim();
                        replaceCellValue(cell, cleanedValue.isEmpty() ? null : cleanedValue);
                        cleaned++;
                    }
                }
            }
        }
        if (cleaned > 0) {
            log.info("cleanupRemainingPlaceholders: {} cells cleaned", cleaned);
        }
    }

    private void embedImages(Workbook workbook, ImagePlacement ip) {
        Sheet sheet = ip.sheet();
        Drawing<?> drawing = sheet.getDrawingPatriarch();
        if (drawing == null) {
            drawing = sheet.createDrawingPatriarch();
        }

        int count = ip.paths().size();
        int col = ip.col();
        int row = ip.row();

        // Calculate cell dimensions once (all images share the same cell)
        double colWidthPt = sheet.getColumnWidth(col) / 256.0 * Units.DEFAULT_CHARACTER_WIDTH;
        int cellW = Units.toEMU(colWidthPt);
        Row r = sheet.getRow(row);
        double rowHeightPt = r != null ? r.getHeightInPoints() : 15;
        int cellH = Units.toEMU(rowHeightPt);

        // Divide cell width equally among images
        int slotW = cellW / count;

        for (int i = 0; i < count; i++) {
            String relativePath = ip.paths().get(i);
            try {
                int sep = relativePath.indexOf('/');
                if (sep < 0) {
                    log.warn("Invalid image path: {}", relativePath);
                    continue;
                }
                String category = relativePath.substring(0, sep);
                String fileName = relativePath.substring(sep + 1);
                Resource imgRes = fileStorageService.load(category, fileName);
                if (imgRes == null) {
                    log.warn("Image file not found on SMB: {}", relativePath);
                    continue;
                }
                byte[] imageBytes;
                try (InputStream is = imgRes.getInputStream()) {
                    imageBytes = IOUtils.toByteArray(is);
                }

                // Read image dimensions for aspect ratio
                int imgW = 0, imgH = 0;
                try (InputStream bais = new ByteArrayInputStream(imageBytes)) {
                    BufferedImage bimg = ImageIO.read(bais);
                    if (bimg != null) {
                        imgW = bimg.getWidth();
                        imgH = bimg.getHeight();
                    }
                } catch (Exception e) {
                    log.warn("Failed to read image dimensions: {}", relativePath);
                }

                int pictureIdx = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_JPEG);
                ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();

                if (imgW > 0 && imgH > 0) {
                    double imgAspect = (double) imgH / imgW;
                    double slotAspect = (double) cellH / slotW;

                    int dispW, dispH;
                    if (imgAspect > slotAspect) {
                        dispH = cellH;
                        dispW = (int) (cellH / imgAspect);
                    } else {
                        dispW = slotW;
                        dispH = (int) (slotW * imgAspect);
                    }

                    int offsetX = i * slotW;

                    // All images in the same cell, arranged left-to-right
                    anchor.setCol1(col);
                    anchor.setCol2(col);
                    anchor.setRow1(row);
                    anchor.setRow2(row);
                    anchor.setDx1(offsetX);
                    anchor.setDy1(0);
                    anchor.setDx2(offsetX + dispW);
                    anchor.setDy2(dispH);
                } else {
                    int offsetX = i * slotW;
                    anchor.setCol1(col);
                    anchor.setCol2(col);
                    anchor.setRow1(row);
                    anchor.setRow2(row);
                    anchor.setDx1(offsetX);
                    anchor.setDy1(0);
                    anchor.setDx2(offsetX + slotW);
                    anchor.setDy2(cellH);
                }
                anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);

                drawing.createPicture(anchor, pictureIdx);
            } catch (Exception e) {
                log.warn("Failed to embed image: path={}, error={}", relativePath, e.getMessage());
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

    /**
     * 替换单元格内容。XSSF 的 setCellValue() 对 inline string 单元格无效（底层 XML 不变），
     * 对空字符串的 setValue/setBlank 也无法清除占位符。唯一可靠方式：从行中物理删除旧格，再创建新格。
     */
    private void replaceCellValue(Cell oldCell, String value) {
        Row row = oldCell.getRow();
        if (row == null) return;
        int colIndex = oldCell.getColumnIndex();
        CellStyle style = oldCell.getCellStyle();

        // 先从行中移除（物理删除底层 XML 的 <c> 元素）
        row.removeCell(oldCell);

        // 空值不重建单元格（相当于占位符被移除，Excel 中该格为空）
        if (value == null || value.isEmpty()) {
            return;
        }

        // 重建单元格并保留样式
        Cell newCell = row.createCell(colIndex);
        newCell.setCellStyle(style);

        // 按原 setCellValue 逻辑，优先尝试数字解析
        try {
            if (style.getDataFormatString() != null && style.getDataFormatString().contains("%")) {
                double numValue = Double.parseDouble(value.replace("%", "").trim());
                newCell.setCellValue(numValue / 100);
            } else {
                double numValue = Double.parseDouble(value);
                newCell.setCellValue(numValue);
            }
        } catch (NumberFormatException e) {
            newCell.setCellValue(value);
        }
    }
}
