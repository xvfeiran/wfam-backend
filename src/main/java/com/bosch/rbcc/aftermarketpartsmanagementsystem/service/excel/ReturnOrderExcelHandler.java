package com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles Excel import/export operations for return orders.
 * Encapsulates all Apache POI logic separate from business logic.
 */
@Service
@RequiredArgsConstructor
public class ReturnOrderExcelHandler {

    private static final String SHEET_NAME = "ReturnOrders";
    private static final String[] HEADERS = {
            "客户", "收货日期", "投诉日期", "退回方式", "物流单号", "退货数量", "描述"
    };

    /**
     * Exports return orders to Excel format.
     *
     * @param orders list of orders to export
     * @return Excel file as byte array
     */
    public byte[] exportToExcel(List<ReturnOrderDTO> orders) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET_NAME);
            createHeaderRow(sheet);
            fillDataRows(sheet, orders);
            autoSizeColumns(sheet);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ExcelOperationException("Excel export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Imports return orders from Excel file.
     *
     * @param file Excel file to import
     * @return list of parsed order DTOs
     */
    public List<ReturnOrderDTO> importFromExcel(MultipartFile file) {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            return parseOrdersFromSheet(sheet);
        } catch (IOException e) {
            throw new ExcelOperationException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }

    private void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            applyHeaderStyle(cell, sheet.getWorkbook());
        }
    }

    private void fillDataRows(Sheet sheet, List<ReturnOrderDTO> orders) {
        int rowIdx = 1;
        for (ReturnOrderDTO dto : orders) {
            Row row = sheet.createRow(rowIdx++);
            fillOrderRow(row, dto);
        }
    }

    private void fillOrderRow(Row row, ReturnOrderDTO dto) {
        row.createCell(0).setCellValue(dto.getCustomer() != null ? dto.getCustomer() : "");
        row.createCell(1).setCellValue(dto.getReceiveDate() != null ? dto.getReceiveDate() : "");
        row.createCell(2).setCellValue(dto.getComplaintDate() != null ? dto.getComplaintDate() : "");
        row.createCell(3).setCellValue(dto.getReturnMethod() != null ? dto.getReturnMethod() : "");
        row.createCell(4).setCellValue(dto.getTrackingNumber() != null ? dto.getTrackingNumber() : "");
        row.createCell(5).setCellValue(dto.getReturnQuantity());
    }

    private List<ReturnOrderDTO> parseOrdersFromSheet(Sheet sheet) {
        List<ReturnOrderDTO> orders = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            try {
                orders.add(parseOrderFromRow(row));
            } catch (Exception e) {
                // Skip invalid rows, could log them separately
                // For now, we'll continue processing other rows
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

    private void applyHeaderStyle(Cell cell, Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        cell.setCellStyle(style);
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Runtime exception for Excel operation failures.
     */
    public static class ExcelOperationException extends RuntimeException {
        public ExcelOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
