package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImportFileSummaryDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImportRecordDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Customer;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ImportRecord;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ImportRecordListView;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ImportRecordRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.CustomerRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel.PartImportParser;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel.ReturnOrderImportParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final String DEFAULT_FILE_ORDER_KEY = "__MISSING_ORDER_NUMBER__";

    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED  = "completed";
    public static final String STATUS_DELETING = "deleting";
    public static final String STATUS_ROLLED_BACK = "rolled_back";

    private static final String TYPE_RETURN_ORDER = "return_order";
    private static final String TYPE_PART = "part";
    private static final String IMPORT_RETURN_METHOD = "express";
    private static final String IMPORT_COMPLAINT_TYPE = "BA40";
    private static final Pattern CUSTOMER_NAME_PATTERN = Pattern.compile("(?i)DATA_(.+?)_Amount");

    private final ReturnOrderImportParser returnOrderImportParser;
    private final PartImportParser partImportParser;
    private final ReturnOrderService returnOrderService;
    private final PartService partService;
    private final PartRepository partRepository;
    private final ImportRecordRepository importRecordRepo;
    private final CustomerRepository customerRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ImportService> selfProvider;
    private final EntityManager entityManager;

    // ─────────────────────────────────────────────
    // 1. 同步：创建"处理中"记录，立即返回
    // ─────────────────────────────────────────────
    @Transactional
    public ImportRecordDTO createPendingRecord(String fileName) {
        log.info("[Import] 创建导入记录: fileName={}", fileName);
        ImportRecord record = ImportRecord.builder()
                .id(UUID.randomUUID().toString())
                .importType(TYPE_RETURN_ORDER)
                .fileName(fileName)
                .status(STATUS_PROCESSING)
                .totalCount(0)
                .successCount(0)
                .failCount(0)
                .failLogs("[]")
                .build();
        importRecordRepo.save(record);
        log.info("[Import] 导入记录已创建: id={}, status=processing", record.getId());
        return toDTO(record, true);
    }

    // ─────────────────────────────────────────────
    // 2. 异步：后台处理 Excel 字节
    //    注意：此方法必须从外部 Bean（Controller）调用，Spring 代理才能拦截 @Async
    // ─────────────────────────────────────────────
    @Async("importTaskExecutor")
    public void processReturnOrdersAsync(String recordId, byte[] fileBytes) {
        log.info("[Import] [{}] 异步任务启动，线程: {}", recordId, Thread.currentThread().getName());

        // 1. 解析 Excel
        List<ReturnOrderImportParser.ParseResult> parseResults;
        try {
            parseResults = returnOrderImportParser.parseBytes(fileBytes);
        } catch (Exception e) {
            log.error("[Import] [{}] 文件解析异常: {}", recordId, e.getMessage(), e);
            markFailed(recordId, "文件解析失败: " + e.getMessage());
            return;
        }

        int totalCount = parseResults.size();
        log.info("[Import] [{}] 解析到 {} 行数据，开始逐行写入", recordId, totalCount);

        // 2. 逐行创建并提交退货单
        int successCount = 0;
        List<Map<String, Object>> failLogEntries   = new ArrayList<>();
        List<Map<String, Object>> importLogEntries = new ArrayList<>();

        for (ReturnOrderImportParser.ParseResult result : parseResults) {
            if (!result.isSuccess()) {
                log.warn("[Import] [{}] 第{}行解析失败: {}", recordId, result.getRowNum(), result.getError());
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("row",     result.getRowNum());
                entry.put("status",  "failed");
                entry.put("error",   result.getError());
                entry.put("rawData", result.getRawData());
                failLogEntries.add(entry);
                importLogEntries.add(entry);
                continue;
            }

            try {
                // createAndSubmitForImport：直接生成单号并置为 in_initial_analysis
                ReturnOrderDTO created = returnOrderService.createAndSubmitForImport(result.getDto());
                successCount++;
                log.debug("[Import] [{}] 第{}行写入成功: orderNumber={}, orderId={}",
                        recordId, result.getRowNum(), created.getOrderNumber(), created.getId());

                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("row",           result.getRowNum());
                entry.put("status",        "success");
                entry.put("orderId",       created.getId());
                entry.put("orderNumber",   created.getOrderNumber());
                entry.put("receiveDate",   created.getReceiveDate());
                entry.put("trackingNumber", created.getTrackingNumber());
                importLogEntries.add(entry);

            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("[Import] [{}] 第{}行写入失败: {}", recordId, result.getRowNum(), errMsg, e);

                Map<String, Object> failEntry = new java.util.LinkedHashMap<>();
                failEntry.put("row",     result.getRowNum());
                failEntry.put("status",  "failed");
                failEntry.put("error",   errMsg);
                failEntry.put("rawData", result.getRawData());
                failLogEntries.add(failEntry);
                importLogEntries.add(failEntry);
            }
        }

        int failCount = totalCount - successCount;
        String failLogs   = serialize(failLogEntries);
        String importLogs = serialize(importLogEntries);

        log.info("[Import] [{}] 处理完成 — 总计: {}, 成功: {}, 失败: {}",
                recordId, totalCount, successCount, failCount);

        // 3. 更新记录为 completed
        markCompleted(recordId, totalCount, successCount, failCount, failLogs, importLogs);
    }

    // ─────────────────────────────────────────────
    // 售后件导入
    // ─────────────────────────────────────────────

    @Transactional
    public ImportRecordDTO createPendingPartRecord(String fileName) {
        log.info("[Import] 创建售后件导入记录: fileName={}", fileName);
        ImportRecord record = ImportRecord.builder()
                .id(UUID.randomUUID().toString())
                .importType(TYPE_PART)
                .fileName(fileName)
                .status(STATUS_PROCESSING)
                .totalCount(0)
                .successCount(0)
                .failCount(0)
                .failLogs("[]")
                .build();
        importRecordRepo.save(record);
        log.info("[Import] 售后件导入记录已创建: id={}, status=processing", record.getId());
        return toDTO(record, true);
    }

    @Async("importTaskExecutor")
    public void processPartsAsync(String recordId, byte[] fileBytes) {
        log.info("[Import] [{}] 售后件异步任务启动，线程: {}", recordId, Thread.currentThread().getName());

        String sourceFileName = importRecordRepo.findById(recordId)
            .map(ImportRecord::getFileName)
            .orElse("uploaded-file.xlsx");

        // 1. 解析 Excel
        List<PartImportParser.ParseResult> parseResults;
        try {
            parseResults = partImportParser.parseBytes(fileBytes);
        } catch (Exception e) {
            log.error("[Import] [{}] 文件解析异常: {}", recordId, e.getMessage(), e);
            markFailed(recordId, "文件解析失败: " + e.getMessage());
            return;
        }

        int totalCount = parseResults.size();
        log.info("[Import] [{}] 解析到 {} 行数据，开始逐行写入", recordId, totalCount);

        ReturnOrderPreparation orderPreparation = prepareReturnOrdersForPartImport(sourceFileName, parseResults);

        // 2. 逐行创建售后件
        int successCount = 0;
        List<Map<String, Object>> failLogEntries   = new ArrayList<>();
        List<Map<String, Object>> importLogEntries = new ArrayList<>();

        for (PartImportParser.ParseResult result : parseResults) {
            if (!result.isSuccess()) {
                log.warn("[Import] [{}] 第{}行解析失败: {}", recordId, result.getRowNum(), result.getError());
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("fileName", sourceFileName);
                entry.put("row",     result.getRowNum());
                entry.put("status",  "failed");
                entry.put("errorCode", classifyErrorCode(result.getError()));
                entry.put("error",   result.getError());
                entry.put("rawData", result.getRawData());
                failLogEntries.add(entry);
                importLogEntries.add(entry);
                continue;
            }

            try {
                String orderNumber = normalizeOrderNumber(result.getDto().getOrderNumber());
                ReturnOrderDTO createdOrder = orderPreparation.createdOrders().get(orderNumber);
                if (createdOrder == null) {
                    throw new IllegalArgumentException(orderPreparation.orderErrors().getOrDefault(orderNumber, "退货单创建失败: " + orderNumber));
                }
                result.getDto().setOrderId(createdOrder.getId());
                result.getDto().setOrderNumber(createdOrder.getOrderNumber());
                PartDTO created = partService.create(result.getDto(), null);
                successCount++;
                log.debug("[Import] [{}] 第{}行写入成功: orderNumber={}, partCode={}",
                        recordId, result.getRowNum(), created.getOrderNumber(), created.getPartCode());

                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("fileName", sourceFileName);
                entry.put("row",         result.getRowNum());
                entry.put("status",      "success");
                entry.put("orderId",     createdOrder.getId());
                entry.put("orderNumber", created.getOrderNumber());
                entry.put("orderCreated", orderPreparation.orderCreatedFlags().getOrDefault(orderNumber, false));
                entry.put("partId",      created.getId());
                entry.put("partCode",    created.getPartCode());
                entry.put("partNumber",  created.getPartNumber());
                entry.put("qcNo",        created.getQcNo());
                importLogEntries.add(entry);

            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("[Import] [{}] 第{}行写入失败: {}", recordId, result.getRowNum(), errMsg, e);

                Map<String, Object> failEntry = new java.util.LinkedHashMap<>();
                failEntry.put("fileName", sourceFileName);
                failEntry.put("row",     result.getRowNum());
                failEntry.put("status",  "failed");
                failEntry.put("errorCode", classifyErrorCode(errMsg));
                failEntry.put("error",   errMsg);
                failEntry.put("rawData", result.getRawData());
                failLogEntries.add(failEntry);
                importLogEntries.add(failEntry);
            }
        }

        int failCount = totalCount - successCount;
        String failLogs   = serialize(failLogEntries);
        String importLogs = serialize(importLogEntries);

        log.info("[Import] [{}] 处理完成 — 总计: {}, 成功: {}, 失败: {}",
                recordId, totalCount, successCount, failCount);

        // 3. 更新记录为 completed
        markCompleted(recordId, totalCount, successCount, failCount, failLogs, importLogs);
    }

    @Async("importTaskExecutor")
    public void processPartsFolderAsync(String recordId, String folderPath) {
        log.info("[Import] [{}] 售后件目录导入异步任务启动，线程: {}", recordId, Thread.currentThread().getName());

        Path root = Paths.get(folderPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            markFailed(recordId, "目录不存在或不是文件夹: " + folderPath);
            return;
        }

        List<Path> excelFiles;
        try (Stream<Path> pathStream = Files.walk(root)) {
            excelFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(this::isExcelFile)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("[Import] [{}] 扫描目录失败: {}", recordId, e.getMessage(), e);
            markFailed(recordId, "扫描目录失败: " + e.getMessage());
            return;
        }

        if (excelFiles.isEmpty()) {
            markFailed(recordId, "目录中未找到 Excel 文件（.xls/.xlsx）");
            return;
        }

        int totalCount = 0;
        int successCount = 0;
        List<Map<String, Object>> failLogEntries = new ArrayList<>();
        List<Map<String, Object>> importLogEntries = new ArrayList<>();

        for (Path excelFile : excelFiles) {
            String relativeFileName = root.relativize(excelFile).toString();
            byte[] fileBytes;
            try {
                fileBytes = Files.readAllBytes(excelFile);
            } catch (IOException e) {
                Map<String, Object> failEntry = new java.util.LinkedHashMap<>();
                failEntry.put("fileName", relativeFileName);
                failEntry.put("row", 0);
                failEntry.put("status", "failed");
                failEntry.put("errorCode", "FILE_READ_FAILED");
                failEntry.put("error", "读取文件失败: " + e.getMessage());
                failLogEntries.add(failEntry);
                importLogEntries.add(failEntry);
                continue;
            }

            List<PartImportParser.ParseResult> parseResults;
            try {
                parseResults = partImportParser.parseBytes(fileBytes);
            } catch (Exception e) {
                Map<String, Object> failEntry = new java.util.LinkedHashMap<>();
                failEntry.put("fileName", relativeFileName);
                failEntry.put("row", 0);
                failEntry.put("status", "failed");
                failEntry.put("errorCode", "FILE_PARSE_FAILED");
                failEntry.put("error", "文件解析失败: " + e.getMessage());
                failLogEntries.add(failEntry);
                importLogEntries.add(failEntry);
                continue;
            }

            totalCount += parseResults.size();
            ReturnOrderPreparation orderPreparation = prepareReturnOrdersForPartImport(relativeFileName, parseResults);

            for (PartImportParser.ParseResult result : parseResults) {
                if (!result.isSuccess()) {
                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    entry.put("fileName", relativeFileName);
                    entry.put("row", result.getRowNum());
                    entry.put("status", "failed");
                    entry.put("errorCode", classifyErrorCode(result.getError()));
                    entry.put("error", result.getError());
                    entry.put("rawData", result.getRawData());
                    failLogEntries.add(entry);
                    importLogEntries.add(entry);
                    continue;
                }

                try {
                    String orderNumber = normalizeOrderNumber(result.getDto().getOrderNumber());
                    ReturnOrderDTO createdOrder = orderPreparation.createdOrders().get(orderNumber);
                    if (createdOrder == null) {
                        throw new IllegalArgumentException(orderPreparation.orderErrors().getOrDefault(orderNumber, "退货单创建失败: " + orderNumber));
                    }
                    result.getDto().setOrderId(createdOrder.getId());
                    result.getDto().setOrderNumber(createdOrder.getOrderNumber());
                    PartDTO created = partService.create(result.getDto(), null);
                    successCount++;

                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    entry.put("fileName", relativeFileName);
                    entry.put("row", result.getRowNum());
                    entry.put("status", "success");
                    entry.put("orderId", createdOrder.getId());
                    entry.put("orderNumber", created.getOrderNumber());
                    entry.put("orderCreated", orderPreparation.orderCreatedFlags().getOrDefault(orderNumber, false));
                    entry.put("partId", created.getId());
                    entry.put("partCode", created.getPartCode());
                    entry.put("partNumber", created.getPartNumber());
                    entry.put("qcNo", created.getQcNo());
                    importLogEntries.add(entry);
                } catch (Exception e) {
                    String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    Map<String, Object> failEntry = new java.util.LinkedHashMap<>();
                    failEntry.put("fileName", relativeFileName);
                    failEntry.put("row", result.getRowNum());
                    failEntry.put("status", "failed");
                    failEntry.put("errorCode", classifyErrorCode(errMsg));
                    failEntry.put("error", errMsg);
                    failEntry.put("rawData", result.getRawData());
                    failLogEntries.add(failEntry);
                    importLogEntries.add(failEntry);
                }
            }
        }

        int failCount = Math.max(0, totalCount - successCount);
        String failLogs = serialize(failLogEntries);
        String importLogs = serialize(importLogEntries);

        log.info("[Import] [{}] 目录导入处理完成 — 文件数: {}, 总计: {}, 成功: {}, 失败: {}",
                recordId, excelFiles.size(), totalCount, successCount, failCount);
        markCompleted(recordId, totalCount, successCount, failCount, failLogs, importLogs);
    }

    // ─────────────────────────────────────────────
    // 3. 查询单条（前端轮询用）
    // ─────────────────────────────────────────────
    public ImportRecordDTO getById(String id) {
        return importRecordRepo.findById(id)
                .map(record -> toDTO(record, false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import record not found: " + id));
    }

    public List<ImportFileSummaryDTO> listImportFiles(String id) {
        ImportRecord record = importRecordRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import record not found: " + id));

        List<Map<String, Object>> rows = deserializeLogs(record.getImportLogs());
        Map<String, ImportFileSummaryDTO> grouped = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String fileName = String.valueOf(row.getOrDefault("fileName", "single-file"));
            String status = String.valueOf(row.getOrDefault("status", "failed"));

            ImportFileSummaryDTO summary = grouped.computeIfAbsent(fileName,
                    k -> ImportFileSummaryDTO.builder().fileName(k).totalCount(0).successCount(0).failCount(0).build());

            summary.setTotalCount(summary.getTotalCount() + 1);
            if ("success".equalsIgnoreCase(status)) {
                summary.setSuccessCount(summary.getSuccessCount() + 1);
            } else {
                summary.setFailCount(summary.getFailCount() + 1);
            }
        }

        return grouped.values().stream()
                .sorted(Comparator.comparing(ImportFileSummaryDTO::getFileName))
                .collect(Collectors.toList());
    }

    public PageImpl<Map<String, Object>> listImportLogsByFile(String id, String fileName, Pageable pageable) {
        ImportRecord record = importRecordRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import record not found: " + id));

        List<Map<String, Object>> rows = deserializeLogs(record.getImportLogs()).stream()
                .filter(row -> fileName.equals(String.valueOf(row.getOrDefault("fileName", "single-file"))))
                .collect(Collectors.toList());

        int total = rows.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<Map<String, Object>> pageData = start >= total ? List.of() : rows.subList(start, end);

        return new PageImpl<>(pageData, pageable, total);
    }

    // ─────────────────────────────────────────────
    // 4. 列表查询
    // ─────────────────────────────────────────────
    public Page<ImportRecordDTO> listImports(String type, Pageable pageable) {
        Page<ImportRecordListView> page = importRecordRepo.findListViewByType(type, pageable);

        List<ImportRecordDTO> dtos = page.getContent().stream()
            .map(this::toListDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    // ─────────────────────────────────────────────
    // 私有工具方法
    // ─────────────────────────────────────────────

    private void markCompleted(String recordId, int total, int success, int fail,
                               String failLogs, String importLogs) {
        importRecordRepo.findById(recordId).ifPresentOrElse(record -> {
            record.setStatus(STATUS_COMPLETED);
            record.setTotalCount(total);
            record.setSuccessCount(success);
            record.setFailCount(fail);
            record.setFailLogs(failLogs);
            record.setImportLogs(importLogs);
            importRecordRepo.save(record);
            log.info("[Import] [{}] 记录已更新为 completed", recordId);
        }, () -> log.error("[Import] [{}] 找不到记录，无法更新状态", recordId));
    }

    private void markFailed(String recordId, String errorMessage) {
        importRecordRepo.findById(recordId).ifPresentOrElse(record -> {
            // Keep lifecycle status as "completed" and express failure in counts/logs.
            record.setStatus(STATUS_COMPLETED);
            record.setTotalCount(1);
            record.setSuccessCount(0);
            record.setFailCount(1);
            record.setFailLogs(serialize(List.of(Map.of("row", 0, "error", errorMessage))));
            record.setImportLogs(serialize(List.of(Map.of("row", 0, "status", "failed", "error", errorMessage))));
            importRecordRepo.save(record);
            log.info("[Import] [{}] 记录已更新为 completed(with failure)", recordId);
        }, () -> log.error("[Import] [{}] 找不到记录，无法标记为 failed", recordId));
    }

    private ImportRecordDTO toDTO(ImportRecord record, boolean includeLogs) {
        return ImportRecordDTO.builder()
                .id(record.getId())
                .importType(record.getImportType())
                .fileName(record.getFileName())
                .status(record.getStatus())
                .totalCount(record.getTotalCount())
                .successCount(record.getSuccessCount())
                .failCount(record.getFailCount())
                .failLogs(includeLogs ? record.getFailLogs() : "[]")
                .importLogs(includeLogs ? record.getImportLogs() : "[]")
                .createdBy(record.getCreatedBy())
                .createdAt(record.getCreatedAt() != null ? record.getCreatedAt().toString() : null)
                .build();
    }

    private String serialize(List<Map<String, Object>> logs) {
        if (logs.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(logs);
        } catch (JsonProcessingException e) {
            log.warn("[Import] 序列化日志出错: {}", e.getMessage());
            return "[]";
        }
    }

    private boolean isExcelFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
    }

    private ReturnOrderPreparation prepareReturnOrdersForPartImport(String sourceFileName,
                                                                    List<PartImportParser.ParseResult> parseResults) {
        String customerName = extractCustomerName(sourceFileName);
        String customerId = resolveCustomerId(customerName);
        Map<String, List<PartImportParser.ParseResult>> groupedResults = parseResults.stream()
                .filter(PartImportParser.ParseResult::isSuccess)
                .collect(Collectors.groupingBy(result -> normalizeOrderNumber(result.getDto().getOrderNumber()), LinkedHashMap::new, Collectors.toList()));

        Map<String, ReturnOrderDTO> createdOrders = new HashMap<>();
        Map<String, String> orderErrors = new HashMap<>();
        Map<String, Boolean> orderCreatedFlags = new HashMap<>();

        for (Map.Entry<String, List<PartImportParser.ParseResult>> entry : groupedResults.entrySet()) {
            String orderNumber = entry.getKey();
            if (DEFAULT_FILE_ORDER_KEY.equals(orderNumber)) {
                orderErrors.put(orderNumber, "退货单号不能为空");
                continue;
            }

            List<PartImportParser.ParseResult> groupRows = entry.getValue();
            String failureDate = extractFirstRawValue(groupRows, "vehicleFailureDate");
            if (failureDate == null) {
                failureDate = LocalDate.now().toString();
            }

            ReturnOrderDTO dto = ReturnOrderDTO.builder()
                    .orderNumber(orderNumber)
                    .customer(customerName)
                    .customerId(customerId)
                    .receiveDate(failureDate)
                    .complaintDate(failureDate)
                    .returnMethod(IMPORT_RETURN_METHOD)
                    .trackingNumber(buildTrackingPlaceholder(orderNumber))
                    .returnQuantity(Math.max(groupRows.size(), 1))
                    .complaintType(IMPORT_COMPLAINT_TYPE)
                    .build();

            try {
                createdOrders.put(orderNumber, returnOrderService.createAndSubmitForImport(dto));
                orderCreatedFlags.put(orderNumber, true);
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                ReturnOrderDTO existingOrder = findExistingOrderAsImportTarget(orderNumber);
                if (existingOrder != null) {
                    createdOrders.put(orderNumber, existingOrder);
                    orderCreatedFlags.put(orderNumber, false);
                    log.debug("[Import] 复用已存在退货单: fileName={}, orderNumber={}, orderId={}",
                            sourceFileName, orderNumber, existingOrder.getId());
                    continue;
                }
                log.warn("[Import] 创建退货单失败: fileName={}, orderNumber={}, error={}", sourceFileName, orderNumber, errMsg, e);
                orderErrors.put(orderNumber, errMsg);
            }
        }

        return new ReturnOrderPreparation(createdOrders, orderErrors, orderCreatedFlags);
    }

    @Transactional
    public ImportRecordDTO requestDeleteImportedData(String importId) {
        ImportRecord record = importRecordRepo.findById(importId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import record not found: " + importId));

        if (STATUS_ROLLED_BACK.equals(record.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "本次导入数据已删除，无需重复操作");
        }
        if (STATUS_DELETING.equals(record.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "本次导入数据正在删除中，请稍后刷新");
        }
        if (!STATUS_COMPLETED.equals(record.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅已结束的导入记录可执行删除");
        }

        record.setStatus(STATUS_DELETING);
        importRecordRepo.save(record);

        // Invoke through Spring proxy so @Async is effective.
        selfProvider.getObject().processDeleteImportedDataAsync(importId);
        return toDTO(record, false);
    }

    @Async("importTaskExecutor")
    public void processDeleteImportedDataAsync(String importId) {
        try {
            selfProvider.getObject().executeDeleteImportedData(importId);
            log.info("[Import] [{}] 删除导入数据完成", importId);
        } catch (Exception e) {
            log.warn("[Import] [{}] 删除导入数据失败: {}", importId, e.getMessage(), e);
            markDeleteFailed(importId, e.getMessage() != null ? e.getMessage() : "删除导入数据失败");
        }
    }

    @Transactional
    public void executeDeleteImportedData(String importId) {
        ImportRecord record = importRecordRepo.findById(importId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import record not found: " + importId));

        List<Map<String, Object>> rows = deserializeLogs(record.getImportLogs());
        List<Map<String, Object>> successRows = rows.stream()
                .filter(row -> "success".equalsIgnoreCase(String.valueOf(row.getOrDefault("status", ""))))
                .collect(Collectors.toList());

        LinkedHashSet<String> orderIdsToDelete = new LinkedHashSet<>();
        if (TYPE_RETURN_ORDER.equals(record.getImportType())) {
            successRows.stream()
                    .map(row -> asText(row.get("orderId")))
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(orderIdsToDelete::add);
        } else if (TYPE_PART.equals(record.getImportType())) {
            successRows.stream()
                    .filter(row -> Boolean.parseBoolean(String.valueOf(row.getOrDefault("orderCreated", "false"))))
                    .map(row -> asText(row.get("orderId")))
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(orderIdsToDelete::add);
        }

        LinkedHashSet<String> partIdsToDelete = successRows.stream()
                .map(row -> asText(row.get("partId")))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Ensure child rows are removed first by deriving part IDs from target orders.
        for (String orderId : orderIdsToDelete) {
            List<Part> orderParts = partRepository.findByOrderId(orderId);
            for (Part orderPart : orderParts) {
                if (orderPart.getId() != null && !orderPart.getId().isBlank()) {
                    partIdsToDelete.add(orderPart.getId());
                }
            }
        }

        int deletedPartCount = 0;
        if (!partIdsToDelete.isEmpty()) {
            List<String> existingPartIds = partRepository.findAllById(partIdsToDelete).stream()
                    .map(part -> part.getId())
                    .collect(Collectors.toList());
            if (!existingPartIds.isEmpty()) {
                partRepository.deleteAllByIdInBatch(existingPartIds);
                entityManager.flush();
                deletedPartCount = existingPartIds.size();
            }
        }

        int deletedOrderCount = 0;
        int skippedOrderCount = 0;
        for (String orderId : orderIdsToDelete) {
            if (partRepository.countByOrderId(orderId) > 0) {
                skippedOrderCount++;
                continue;
            }
            if (returnOrderRepository.existsById(orderId)) {
                returnOrderRepository.deleteById(orderId);
                deletedOrderCount++;
            }
        }

        log.debug("[Import] [{}] 删除结果: deletedPartCount={}, deletedOrderCount={}, skippedOrderCount={}",
                importId, deletedPartCount, deletedOrderCount, skippedOrderCount);
        record.setStatus(STATUS_ROLLED_BACK);
        importRecordRepo.save(record);
    }

    @Transactional
    protected void markDeleteFailed(String importId, String errorMessage) {
        importRecordRepo.findById(importId).ifPresent(record -> {
            record.setStatus(STATUS_COMPLETED);
            importRecordRepo.save(record);

            String msg = errorMessage == null || errorMessage.isBlank() ? "删除导入数据失败" : errorMessage;
            log.warn("[Import] [{}] 删除任务失败，状态回滚为 completed: {}", importId, msg);
        });
    }

    private String extractFirstRawValue(List<PartImportParser.ParseResult> parseResults, String key) {
        return parseResults.stream()
                .map(PartImportParser.ParseResult::getRawData)
                .map(raw -> raw.get(key))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private String extractCustomerName(String sourceFileName) {
        String fileName = Paths.get(sourceFileName).getFileName().toString();
        Matcher matcher = CUSTOMER_NAME_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String resolveCustomerId(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            return null;
        }
        Optional<Customer> customer = customerRepository.findByName(customerName);
        return customer.map(Customer::getId).orElse(null);
    }

    private String buildTrackingPlaceholder(String orderNumber) {
        String placeholder = "IMPORT-" + orderNumber;
        return placeholder.length() <= 50 ? placeholder : placeholder.substring(0, 50);
    }

    private ReturnOrderDTO findExistingOrderAsImportTarget(String orderNumber) {
        return returnOrderRepository.findByOrderNumber(orderNumber)
                .map(this::toMinimalOrderDTO)
                .orElse(null);
    }

    private ReturnOrderDTO toMinimalOrderDTO(ReturnOrder order) {
        return ReturnOrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .build();
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return DEFAULT_FILE_ORDER_KEY;
        }
        return orderNumber.trim();
    }

    private String classifyErrorCode(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "UNKNOWN_ERROR";
        }
        String msg = errorMessage.toLowerCase();
        if (msg.contains("order") && msg.contains("not found")) return "ORDER_NOT_FOUND";
        if (msg.contains("退货单号不存在")) return "ORDER_NOT_FOUND";
        if (msg.contains("退货单号已存在")) return "DUPLICATE_ORDER_NUMBER";
        if (msg.contains("零件号主数据不存在") || msg.contains("未映射事业部") || msg.contains("未映射产品平台")) return "PART_CODE_MAPPING_NOT_FOUND";
        if (msg.contains("不能为空") || msg.contains("required")) return "REQUIRED_FIELD_MISSING";
        if (msg.contains("日期") && msg.contains("解析")) return "INVALID_DATE_FORMAT";
        if (msg.contains("数字") && msg.contains("解析")) return "INVALID_NUMBER_FORMAT";
        if (msg.contains("文件解析失败")) return "FILE_PARSE_FAILED";
        if (msg.contains("读取文件失败")) return "FILE_READ_FAILED";
        return "IMPORT_ROW_FAILED";
    }

    private ImportRecordDTO toListDTO(ImportRecordListView row) {
        return ImportRecordDTO.builder()
                .id(row.getId())
                .importType(row.getImportType())
                .fileName(row.getFileName())
                .status(row.getStatus())
                .totalCount(row.getTotalCount())
                .successCount(row.getSuccessCount())
                .failCount(row.getFailCount())
                .failLogs("[]")
                .importLogs("[]")
                .createdBy(row.getCreatedBy())
                .createdAt(row.getCreatedAt() != null ? row.getCreatedAt().toString() : null)
                .build();
    }

    private List<Map<String, Object>> deserializeLogs(String importLogs) {
        if (importLogs == null || importLogs.isBlank() || "[]".equals(importLogs)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(importLogs, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Import] 反序列化 importLogs 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private record ReturnOrderPreparation(
            Map<String, ReturnOrderDTO> createdOrders,
            Map<String, String> orderErrors,
            Map<String, Boolean> orderCreatedFlags
    ) {}
}
