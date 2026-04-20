package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImportFileSummaryDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImportRecordDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Customer;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ImportLogDetail;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ImportRecord;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ImportRecordListView;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ImportRecordRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ImportLogDetailRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.CustomerRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisReportRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.OcrTaskRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_DELETING = "deleting";
    public static final String STATUS_TIMEOUT = "timeout";
    public static final String STATUS_ROLLED_BACK = "rolled_back";

    private static final String TYPE_RETURN_ORDER = "return_order";
    private static final String TYPE_PART = "part";
    private static final String IMPORT_RETURN_METHOD = "express";
    private static final String IMPORT_COMPLAINT_TYPE = "BA40";
    private static final int DELETE_BATCH_SIZE = 500;
    private static final int IMPORT_TIMEOUT_MINUTES = 60;
    private static final int PROGRESS_FLUSH_INTERVAL = 20;
    private static final int BATCH_SAVE_SIZE = 200; // 批量保存大小，与 hibernate.jdbc.batch_size 对齐
    private static final Pattern CUSTOMER_NAME_PATTERN = Pattern.compile("(?i)DATA_(.+?)_Amount");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");
    private static final Pattern MONTH_TEXT_PATTERN = Pattern.compile("(?<!\\d)(1[0-2]|0?[1-9])\\s*月");

    private static final List<String> MONTH_FIELD_KEYS = List.of(
            "month", "Month", "月份", "月",
            "收货时间", "vehicleFailureDate", "失效日期", "车辆故障日期", "vehicleProductionDate", "购车日期");

    private static final List<DateTimeFormatter> MONTH_DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy.M.dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"));

    private final ReturnOrderImportParser returnOrderImportParser;
    private final PartImportParser partImportParser;
    private final ReturnOrderService returnOrderService;
    private final PartService partService;
    private final PartRepository partRepository;
    private final ImportRecordRepository importRecordRepo;
    private final CustomerRepository customerRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final AnalysisOrderRepository analysisOrderRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final OcrTaskRepository ocrTaskRepository;
    private final ImportLogDetailRepository importLogDetailRepo;
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
    // 注意：此方法必须从外部 Bean（Controller）调用，Spring 代理才能拦截 @Async
    // ─────────────────────────────────────────────
    @Async("importTaskExecutor")
    public void processReturnOrdersAsync(String recordId, byte[] fileBytes) {
        log.info("[Import] [{}] 异步任务启动，线程: {}", recordId, Thread.currentThread().getName());

        String sourceFileName = importRecordRepo.findById(recordId)
                .map(ImportRecord::getFileName)
                .orElse("uploaded-file.xlsx");

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
        log.info("[Import] [{}] 解析到 {} 行数据，开始批量写入", recordId, totalCount);

        // 2. 批量创建并提交退货单
        int successCount = 0;
        List<Map<String, Object>> failLogEntries = new ArrayList<>();
        List<Map<String, Object>> importLogEntries = new ArrayList<>();
        List<ImportLogDetail> logDetailBuffer = new ArrayList<>(BATCH_SAVE_SIZE);

        // 批量缓冲区
        List<ReturnOrderDTO> batchBuffer = new ArrayList<>(BATCH_SAVE_SIZE);
        int processedCount = 0;

        for (ReturnOrderImportParser.ParseResult result : parseResults) {
            if (!result.isSuccess()) {
                log.warn("[Import] [{}] 第{}行解析失败: {}", recordId, result.getRowNum(), result.getError());

                // 记录失败日志
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("row", result.getRowNum());
                entry.put("status", "failed");
                entry.put("error", result.getError());
                entry.put("rawData", result.getRawData());
                failLogEntries.add(entry);
                importLogEntries.add(entry);

                // 添加到日志明细缓冲区
                logDetailBuffer.add(ImportLogDetail.builder()
                        .id(UUID.randomUUID().toString())
                        .importId(recordId)
                        .rowNumber(result.getRowNum())
                        .status("failed")
                        .errorMessage(result.getError())
                        .rawData(serializeRawData(result.getRawData()))
                        .createdAt(LocalDateTime.now())
                        .build());

                processedCount++;
                flushBatchIfNeeded(recordId, totalCount, successCount, processedCount,
                        failLogEntries, importLogEntries, logDetailBuffer, batchBuffer);
                continue;
            }

            try {
                // 设置导入创建时间
                LocalDateTime importCreatedAt = resolveImportCreatedAt(sourceFileName, result.getRawData());
                result.getDto().setCreatedAt(importCreatedAt != null ? importCreatedAt.toString() : null);
                batchBuffer.add(result.getDto());
                processedCount++;

                // 每200行批量保存一次
                if (batchBuffer.size() >= BATCH_SAVE_SIZE) {
                    List<ReturnOrderDTO> created = returnOrderService.createAndSubmitBatchForImport(batchBuffer);
                    successCount += created.size();

                    // 记录成功日志
                    for (int i = 0; i < created.size(); i++) {
                        ReturnOrderDTO order = created.get(i);
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("row", parseResults.get(processedCount - created.size() + i).getRowNum());
                        entry.put("status", "success");
                        entry.put("orderId", order.getId());
                        entry.put("orderNumber", order.getOrderNumber());
                        entry.put("receiveDate", order.getReceiveDate());
                        entry.put("trackingNumber", order.getTrackingNumber());
                        importLogEntries.add(entry);

                        // 添加到日志明细缓冲区
                        logDetailBuffer.add(ImportLogDetail.builder()
                                .id(UUID.randomUUID().toString())
                                .importId(recordId)
                                .rowNumber(parseResults.get(processedCount - created.size() + i).getRowNum())
                                .status("success")
                                .orderId(order.getId())
                                .orderNumber(order.getOrderNumber())
                                .createdAt(LocalDateTime.now())
                                .build());
                    }
                    batchBuffer.clear();
                }

                flushBatchIfNeeded(recordId, totalCount, successCount, processedCount,
                        failLogEntries, importLogEntries, logDetailBuffer, batchBuffer);

            } catch (Exception e) {
                String errMsg = resolveImportErrorMessage(e);
                log.warn("[Import] [{}] 第{}行写入失败: {}", recordId, result.getRowNum(), errMsg, e);

                Map<String, Object> failEntry = new LinkedHashMap<>();
                failEntry.put("row", result.getRowNum());
                failEntry.put("status", "failed");
                failEntry.put("error", errMsg);
                failEntry.put("rawData", result.getRawData());
                failLogEntries.add(failEntry);
                importLogEntries.add(failEntry);

                // 添加到日志明细缓冲区
                logDetailBuffer.add(ImportLogDetail.builder()
                        .id(UUID.randomUUID().toString())
                        .importId(recordId)
                        .rowNumber(result.getRowNum())
                        .status("failed")
                        .errorMessage(errMsg)
                        .rawData(serializeRawData(result.getRawData()))
                        .createdAt(LocalDateTime.now())
                        .build());

                flushBatchIfNeeded(recordId, totalCount, successCount, processedCount,
                        failLogEntries, importLogEntries, logDetailBuffer, batchBuffer);
            }
        }

        // 处理剩余记录
        if (!batchBuffer.isEmpty()) {
            List<ReturnOrderDTO> created = returnOrderService.createAndSubmitBatchForImport(batchBuffer);
            successCount += created.size();
            // 记录成功日志
            for (int i = 0; i < created.size(); i++) {
                ReturnOrderDTO order = created.get(i);
                // 找到对应的原始结果来获取行号
                int resultIndex = processedCount - created.size() + i;
                if (resultIndex >= 0 && resultIndex < parseResults.size()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("row", parseResults.get(resultIndex).getRowNum());
                    entry.put("status", "success");
                    entry.put("orderId", order.getId());
                    entry.put("orderNumber", order.getOrderNumber());
                    entry.put("receiveDate", order.getReceiveDate());
                    entry.put("trackingNumber", order.getTrackingNumber());
                    importLogEntries.add(entry);
                }
            }
        }

        // 保存剩余日志明细
        if (!logDetailBuffer.isEmpty()) {
            importLogDetailRepo.saveAll(logDetailBuffer);
            entityManager.flush();
            entityManager.clear();
        }

        int failCount = totalCount - successCount;
        String failLogs = serialize(failLogEntries);
        String importLogs = serialize(importLogEntries);

        log.info("[Import] [{}] 处理完成 — 总计: {}, 成功: {}, 失败: {}",
                recordId, totalCount, successCount, failCount);

        // 3. 更新记录为 completed（此时才写入CLOB）
        markCompleted(recordId, totalCount, successCount, failCount, failLogs, importLogs);
    }

    // ─────────────────────────────────────────────
    // 售后件导入
    // ─────────────────────────────────────────────

    @Transactional
    public ImportRecordDTO createPendingPartRecord(String fileName) {
        log.info("[Import] Creating part import record: fileName={}", escapeForLog(fileName));
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
        log.info("[Import] Part import record created: id={}, status=processing", record.getId());
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
        log.info("[Import] [{}] 解析到 {} 行数据，开始批量写入", recordId, totalCount);

        ReturnOrderPreparation orderPreparation = prepareReturnOrdersForPartImport(sourceFileName, parseResults);

        // 2. 批量创建售后件
        int successCount = 0;
        List<Map<String, Object>> failLogEntries = new ArrayList<>();
        List<Map<String, Object>> importLogEntries = new ArrayList<>();
        List<ImportLogDetail> logDetailBuffer = new ArrayList<>(BATCH_SAVE_SIZE);

        // 批量缓冲区
        List<PartDTO> batchBuffer = new ArrayList<>(BATCH_SAVE_SIZE);
        int processedCount = 0;

        for (PartImportParser.ParseResult result : parseResults) {
            if (!result.isSuccess()) {
                log.warn("[Import] [{}] 第{}行解析失败: {}", recordId, result.getRowNum(), result.getError());

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("fileName", sourceFileName);
                entry.put("row", result.getRowNum());
                entry.put("status", "failed");
                entry.put("errorCode", classifyErrorCode(result.getError()));
                entry.put("error", result.getError());
                entry.put("rawData", result.getRawData());
                failLogEntries.add(entry);
                importLogEntries.add(entry);

                // 添加到日志明细缓冲区
                logDetailBuffer.add(ImportLogDetail.builder()
                        .id(UUID.randomUUID().toString())
                        .importId(recordId)
                        .fileName(sourceFileName)
                        .rowNumber(result.getRowNum())
                        .status("failed")
                        .errorCode(classifyErrorCode(result.getError()))
                        .errorMessage(result.getError())
                        .rawData(serializeRawData(result.getRawData()))
                        .createdAt(LocalDateTime.now())
                        .build());

                processedCount++;
                flushPartBatchIfNeeded(recordId, totalCount, successCount, processedCount,
                        failLogEntries, importLogEntries, logDetailBuffer, batchBuffer);
                continue;
            }

            try {
                String orderNumber = normalizeOrderNumber(result.getDto().getOrderNumber());
                ReturnOrderDTO createdOrder = orderPreparation.createdOrders().get(orderNumber);
                if (createdOrder == null) {
                    throw new IllegalArgumentException(
                            orderPreparation.orderErrors().getOrDefault(orderNumber, "退货单创建失败: " + orderNumber));
                }
                result.getDto().setOrderId(createdOrder.getId());
                result.getDto().setOrderNumber(createdOrder.getOrderNumber());
                LocalDateTime importCreatedAt = resolveImportCreatedAt(sourceFileName, result.getRawData());
                result.getDto().setCreatedAt(importCreatedAt != null ? importCreatedAt.toString() : null);
                batchBuffer.add(result.getDto());
                processedCount++;

                // 每200行批量保存一次
                if (batchBuffer.size() >= BATCH_SAVE_SIZE) {
                    List<PartDTO> created = partService.createForImportBatch(batchBuffer);
                    successCount += created.size();

                    // 记录成功日志
                    for (int i = 0; i < created.size(); i++) {
                        PartDTO part = created.get(i);
                        String orderNum = part.getOrderNumber();
                        boolean orderCreated = orderPreparation.orderCreatedFlags().getOrDefault(orderNum, false);

                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("fileName", sourceFileName);
                        entry.put("status", "success");
                        entry.put("orderId", createdOrder.getId());
                        entry.put("orderNumber", orderNum);
                        entry.put("orderCreated", orderCreated);
                        entry.put("partId", part.getId());
                        entry.put("partCode", part.getPartCode());
                        entry.put("partNumber", part.getPartNumber());
                        entry.put("qcNo", part.getQcNo());
                        importLogEntries.add(entry);

                        // 添加到日志明细缓冲区
                        logDetailBuffer.add(ImportLogDetail.builder()
                                .id(UUID.randomUUID().toString())
                                .importId(recordId)
                                .fileName(sourceFileName)
                                .rowNumber(parseResults.get(processedCount - created.size() + i).getRowNum())
                                .status("success")
                                .orderId(createdOrder.getId())
                                .orderNumber(orderNum)
                                .orderCreated(orderCreated ? "Y" : "N")
                                .partId(part.getId())
                                .partCode(part.getPartCode())
                                .partNumber(part.getPartNumber())
                                .qcNo(part.getQcNo())
                                .createdAt(LocalDateTime.now())
                                .build());
                    }
                    batchBuffer.clear();
                }

                flushPartBatchIfNeeded(recordId, totalCount, successCount, processedCount,
                        failLogEntries, importLogEntries, logDetailBuffer, batchBuffer);

            } catch (Exception e) {
                String errMsg = resolveImportErrorMessage(e);
                log.warn("[Import] [{}] 第{}行写入失败: {}", recordId, result.getRowNum(), errMsg, e);

                Map<String, Object> failEntry = new LinkedHashMap<>();
                failEntry.put("fileName", sourceFileName);
                failEntry.put("row", result.getRowNum());
                failEntry.put("status", "failed");
                failEntry.put("errorCode", classifyErrorCode(errMsg));
                failEntry.put("error", errMsg);
                failEntry.put("rawData", result.getRawData());
                failLogEntries.add(failEntry);
                importLogEntries.add(failEntry);

                // 添加到日志明细缓冲区
                logDetailBuffer.add(ImportLogDetail.builder()
                        .id(UUID.randomUUID().toString())
                        .importId(recordId)
                        .fileName(sourceFileName)
                        .rowNumber(result.getRowNum())
                        .status("failed")
                        .errorCode(classifyErrorCode(errMsg))
                        .errorMessage(errMsg)
                        .rawData(serializeRawData(result.getRawData()))
                        .createdAt(LocalDateTime.now())
                        .build());

                flushPartBatchIfNeeded(recordId, totalCount, successCount, processedCount,
                        failLogEntries, importLogEntries, logDetailBuffer, batchBuffer);
            }
        }

        // 处理剩余记录
        if (!batchBuffer.isEmpty()) {
            List<PartDTO> created = partService.createForImportBatch(batchBuffer);
            successCount += created.size();
            // 记录成功日志
            for (int i = 0; i < created.size(); i++) {
                PartDTO part = created.get(i);
                // 找到对应的原始结果来获取行号
                int resultIndex = processedCount - created.size() + i;
                if (resultIndex >= 0 && resultIndex < parseResults.size()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("fileName", sourceFileName);
                    entry.put("row", parseResults.get(resultIndex).getRowNum());
                    entry.put("status", "success");
                    entry.put("orderId", part.getOrderId());
                    entry.put("orderNumber", part.getOrderNumber());
                    entry.put("partId", part.getId());
                    entry.put("partCode", part.getPartCode());
                    entry.put("partNumber", part.getPartNumber());
                    entry.put("qcNo", part.getQcNo());
                    importLogEntries.add(entry);
                }
            }
        }

        // 保存剩余日志明细
        if (!logDetailBuffer.isEmpty()) {
            importLogDetailRepo.saveAll(logDetailBuffer);
            entityManager.flush();
            entityManager.clear();
        }

        int failCount = totalCount - successCount;
        String failLogs = serialize(failLogEntries);
        String importLogs = serialize(importLogEntries);

        log.info("[Import] [{}] 处理完成 — 总计: {}, 成功: {}, 失败: {}",
                recordId, totalCount, successCount, failCount);

        // 3. 更新记录为 completed（此时才写入CLOB）
        markCompleted(recordId, totalCount, successCount, failCount, failLogs, importLogs);
    }

    /**
     * 目录导入专用：批量写入 pending 的售后件，批次失败时降级为逐条写入。
     * 返回成功写入条数；保证不抛异常，失败行已记入 failLogEntries。
     */
    private int flushPendingPartBatch(List<PendingPartImport> pendingBatch,
            List<Map<String, Object>> importLogEntries,
            List<Map<String, Object>> failLogEntries) {
        if (pendingBatch.isEmpty()) {
            return 0;
        }

        List<PartDTO> dtos = pendingBatch.stream()
                .map(p -> p.result().getDto())
                .collect(Collectors.toList());

        try {
            List<PartDTO> created = partService.createForImportBatch(dtos);
            int n = Math.min(created.size(), pendingBatch.size());
            for (int i = 0; i < n; i++) {
                recordPartImportSuccess(importLogEntries, pendingBatch.get(i), created.get(i));
            }
            return n;
        } catch (Exception batchEx) {
            log.warn("[Import] 批量写入失败，降级为逐条写入: batchSize={}, error={}",
                    pendingBatch.size(), batchEx.getMessage());

            int success = 0;
            for (PendingPartImport pending : pendingBatch) {
                try {
                    PartDTO created = partService.createForImport(pending.result().getDto());
                    recordPartImportSuccess(importLogEntries, pending, created);
                    success++;
                } catch (Exception rowEx) {
                    String errMsg = resolveImportErrorMessage(rowEx);
                    log.warn("[Import] 第{}行写入失败 (降级路径): file={}, error={}",
                            pending.result().getRowNum(), pending.fileName(), errMsg);
                    Map<String, Object> failEntry = new java.util.LinkedHashMap<>();
                    failEntry.put("fileName", pending.fileName());
                    failEntry.put("row", pending.result().getRowNum());
                    failEntry.put("status", "failed");
                    failEntry.put("errorCode", classifyErrorCode(errMsg));
                    failEntry.put("error", errMsg);
                    failEntry.put("rawData", pending.result().getRawData());
                    failLogEntries.add(failEntry);
                    importLogEntries.add(failEntry);
                }
            }
            return success;
        }
    }

    private void recordPartImportSuccess(List<Map<String, Object>> importLogEntries,
            PendingPartImport pending,
            PartDTO part) {
        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("fileName", pending.fileName());
        entry.put("row", pending.result().getRowNum());
        entry.put("status", "success");
        entry.put("orderId", pending.createdOrder().getId());
        entry.put("orderNumber", part.getOrderNumber());
        entry.put("orderCreated", pending.orderCreated());
        entry.put("partId", part.getId());
        entry.put("partCode", part.getPartCode());
        entry.put("partNumber", part.getPartNumber());
        entry.put("qcNo", part.getQcNo());
        importLogEntries.add(entry);
    }

    /**
     * 售后件批量刷新：如果达到批量大小或进度更新间隔，则批量保存日志和进度
     */
    private void flushPartBatchIfNeeded(String recordId,
            int total,
            int success,
            int processed,
            List<Map<String, Object>> failLogEntries,
            List<Map<String, Object>> importLogEntries,
            List<ImportLogDetail> logDetailBuffer,
            List<PartDTO> batchBuffer) {
        // 检查是否需要保存日志明细
        boolean shouldFlushLogs = logDetailBuffer.size() >= BATCH_SAVE_SIZE;
        if (shouldFlushLogs) {
            importLogDetailRepo.saveAll(logDetailBuffer);
            entityManager.flush();
            entityManager.clear();
            logDetailBuffer.clear();
        }

        // 更新进度（使用轻量级方法，通过 selfProvider 确保 @Transactional 生效）
        selfProvider.getObject().flushProgressIfNeeded(recordId, total, success, processed, failLogEntries,
                importLogEntries);
    }

    @Async("importTaskExecutor")
    public void processPartsFolderAsync(String recordId, String folderPath) {
        log.info("[Import] [{}] Part folder import async task started, thread={} folderPath={}",
                recordId, Thread.currentThread().getName(), escapeForLog(folderPath));

        Path root;
        try {
            root = Paths.get(folderPath);
        } catch (InvalidPathException ex) {
            markFailed(recordId, "目录路径无效: " + ex.getInput());
            log.warn("[Import] [{}] 目录路径无效: {}", recordId, ex.getInput());
            return;
        } catch (Exception ex) {
            markFailed(recordId, "目录路径解析失败: " + ex.getMessage());
            log.warn("[Import] [{}] 目录路径解析失败: {}", recordId, ex.getMessage(), ex);
            return;
        }

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
        int processedCount = 0;
        List<Map<String, Object>> failLogEntries = new ArrayList<>();
        List<Map<String, Object>> importLogEntries = new ArrayList<>();
        List<PendingPartImport> pendingBatch = new ArrayList<>(BATCH_SAVE_SIZE);

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
                processedCount++;
                selfProvider.getObject().flushProgressIfNeeded(recordId, Math.max(totalCount, processedCount),
                        successCount, processedCount, failLogEntries, importLogEntries);
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
                processedCount++;
                selfProvider.getObject().flushProgressIfNeeded(recordId, Math.max(totalCount, processedCount),
                        successCount, processedCount, failLogEntries, importLogEntries);
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
                    processedCount++;
                    selfProvider.getObject().flushProgressIfNeeded(recordId, totalCount, successCount, processedCount,
                            failLogEntries, importLogEntries);
                    continue;
                }

                try {
                    String orderNumber = normalizeOrderNumber(result.getDto().getOrderNumber());
                    ReturnOrderDTO createdOrder = orderPreparation.createdOrders().get(orderNumber);
                    if (createdOrder == null) {
                        throw new IllegalArgumentException(
                                orderPreparation.orderErrors().getOrDefault(orderNumber, "退货单创建失败: " + orderNumber));
                    }
                    result.getDto().setOrderId(createdOrder.getId());
                    result.getDto().setOrderNumber(createdOrder.getOrderNumber());
                    LocalDateTime importCreatedAt = resolveImportCreatedAt(relativeFileName, result.getRawData());
                    result.getDto().setCreatedAt(importCreatedAt != null ? importCreatedAt.toString() : null);

                    pendingBatch.add(new PendingPartImport(
                            result, relativeFileName, createdOrder,
                            orderPreparation.orderCreatedFlags().getOrDefault(orderNumber, false)));

                    if (pendingBatch.size() >= BATCH_SAVE_SIZE) {
                        int created = flushPendingPartBatch(pendingBatch, importLogEntries, failLogEntries);
                        successCount += created;
                        processedCount += pendingBatch.size();
                        pendingBatch.clear();
                        selfProvider.getObject().flushProgressNow(recordId, totalCount, successCount, processedCount);
                    }
                } catch (Exception e) {
                    String errMsg = resolveImportErrorMessage(e);
                    Map<String, Object> failEntry = new java.util.LinkedHashMap<>();
                    failEntry.put("fileName", relativeFileName);
                    failEntry.put("row", result.getRowNum());
                    failEntry.put("status", "failed");
                    failEntry.put("errorCode", classifyErrorCode(errMsg));
                    failEntry.put("error", errMsg);
                    failEntry.put("rawData", result.getRawData());
                    failLogEntries.add(failEntry);
                    importLogEntries.add(failEntry);
                    processedCount++;
                    selfProvider.getObject().flushProgressIfNeeded(recordId, totalCount, successCount, processedCount,
                            failLogEntries, importLogEntries);
                }
            }
        }

        // flush 剩余批次
        if (!pendingBatch.isEmpty()) {
            int created = flushPendingPartBatch(pendingBatch, importLogEntries, failLogEntries);
            successCount += created;
            processedCount += pendingBatch.size();
            pendingBatch.clear();
            selfProvider.getObject().flushProgressNow(recordId, totalCount, successCount, processedCount);
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
    @Transactional(readOnly = true)
    public ImportRecordDTO getById(String id) {
        transitionProcessingToTimeoutIfNeeded();
        return importRecordRepo.findById(id)
                .map(record -> toDTO(record, false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import record not found: " + id));
    }

    public List<ImportFileSummaryDTO> listImportFiles(String id) {
        transitionProcessingToTimeoutIfNeeded();

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
        transitionProcessingToTimeoutIfNeeded();

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

    public PageImpl<Map<String, Object>> listImportErrors(String id, Pageable pageable) {
        transitionProcessingToTimeoutIfNeeded();

        ImportRecord record = importRecordRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import record not found: " + id));

        List<Map<String, Object>> rows = deserializeLogs(record.getFailLogs());

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
        transitionProcessingToTimeoutIfNeeded();
        Page<ImportRecordListView> page = importRecordRepo.findListViewByType(type, pageable);

        List<ImportRecordDTO> dtos = page.getContent().stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    // ─────────────────────────────────────────────
    // 私有工具方法
    // ─────────────────────────────────────────────

    @Transactional
    protected void transitionProcessingToTimeoutIfNeeded() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(IMPORT_TIMEOUT_MINUTES);
        List<ImportRecord> records = importRecordRepo.findByStatusAndCreatedAtBefore(STATUS_PROCESSING, threshold);
        if (records.isEmpty()) {
            return;
        }

        for (ImportRecord record : records) {
            record.setStatus(STATUS_TIMEOUT);
        }
        importRecordRepo.saveAll(records);
        log.warn(
                "[Import] Request-triggered timeout transition: {} processing records exceeded {} minutes without heartbeat; status set to timeout",
                records.size(), IMPORT_TIMEOUT_MINUTES);
    }

    private void markCompleted(String recordId, int total, int success, int fail,
            String failLogs, String importLogs) {
        importRecordRepo.findById(recordId).ifPresentOrElse(record -> {
            if (STATUS_TIMEOUT.equals(record.getStatus())) {
                record.setTotalCount(total);
                record.setSuccessCount(success);
                record.setFailCount(fail);
                record.setFailLogs(failLogs);
                record.setImportLogs(importLogs);
                importRecordRepo.save(record);
                log.warn("[Import] [{}] 记录处于 timeout，已同步最终统计和日志", recordId);
            } else {
                record.setStatus(STATUS_COMPLETED);
                record.setTotalCount(total);
                record.setSuccessCount(success);
                record.setFailCount(fail);
                record.setFailLogs(failLogs);
                record.setImportLogs(importLogs);
                importRecordRepo.save(record);
                log.info("[Import] [{}] 记录已更新为 completed", recordId);
            }
        }, () -> log.error("[Import] [{}] 找不到记录，无法更新状态", recordId));
    }

    private void markFailed(String recordId, String errorMessage) {
        importRecordRepo.findById(recordId).ifPresentOrElse(record -> {
            if (STATUS_TIMEOUT.equals(record.getStatus())) {
                record.setTotalCount(Math.max(record.getTotalCount() != null ? record.getTotalCount() : 0, 1));
                record.setSuccessCount(record.getSuccessCount() != null ? record.getSuccessCount() : 0);
                record.setFailCount(Math.max(record.getFailCount() != null ? record.getFailCount() : 0, 1));
                if (record.getFailLogs() == null || record.getFailLogs().isBlank()
                        || "[]".equals(record.getFailLogs())) {
                    record.setFailLogs(serialize(List.of(Map.of("row", 0, "error", errorMessage))));
                }
                if (record.getImportLogs() == null || record.getImportLogs().isBlank()
                        || "[]".equals(record.getImportLogs())) {
                    record.setImportLogs(
                            serialize(List.of(Map.of("row", 0, "status", "failed", "error", errorMessage))));
                }
                importRecordRepo.save(record);
                log.warn("[Import] [{}] 记录处于 timeout，已补充失败信息", recordId);
            } else {
                // Keep lifecycle status as "completed" and express failure in counts/logs.
                record.setStatus(STATUS_COMPLETED);
                record.setTotalCount(1);
                record.setSuccessCount(0);
                record.setFailCount(1);
                record.setFailLogs(serialize(List.of(Map.of("row", 0, "error", errorMessage))));
                record.setImportLogs(serialize(List.of(Map.of("row", 0, "status", "failed", "error", errorMessage))));
                importRecordRepo.save(record);
                log.info("[Import] [{}] 记录已更新为 completed(with failure)", recordId);
            }
        }, () -> log.error("[Import] [{}] 找不到记录，无法标记为 failed", recordId));
    }

    /**
     * 进度更新优化：使用轻量级方法仅更新计数器，避免频繁更新CLOB字段
     * CLOB字段仅在导入结束时通过 markCompleted 写入一次
     * 使用 REQUIRES_NEW 确保每次进度更新都在独立事务中执行，立即提交
     * 注意：此方法需要是 public 以便 @Transactional 生效
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flushProgressIfNeeded(String recordId,
            int total,
            int success,
            int processed,
            List<Map<String, Object>> failLogEntries,
            List<Map<String, Object>> importLogEntries) {
        if (processed <= 0 || processed % PROGRESS_FLUSH_INTERVAL != 0) {
            return;
        }

        // 使用轻量级方法仅更新计数器字段，不触碰CLOB
        importRecordRepo.updateProgressCounters(recordId,
                Math.max(total, processed),
                success,
                Math.max(0, processed - success),
                LocalDateTime.now());

        log.debug("[Import] [{}] 进度更新: total={}, success={}, processed={}",
                recordId, total, success, processed);
    }

    /**
     * 无条件进度更新：调用即提交，不受 PROGRESS_FLUSH_INTERVAL 节流限制。
     * 用于批量模式下每批 flush 后强制同步进度（批次本身每 200 行才触发一次，频率已足够低）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flushProgressNow(String recordId, int total, int success, int processed) {
        if (processed <= 0) {
            return;
        }
        importRecordRepo.updateProgressCounters(recordId,
                Math.max(total, processed),
                success,
                Math.max(0, processed - success),
                LocalDateTime.now());
    }

    /**
     * 批量刷新：如果达到批量大小或进度更新间隔，则批量保存日志和进度
     */
    private void flushBatchIfNeeded(String recordId,
            int total,
            int success,
            int processed,
            List<Map<String, Object>> failLogEntries,
            List<Map<String, Object>> importLogEntries,
            List<ImportLogDetail> logDetailBuffer,
            List<ReturnOrderDTO> batchBuffer) {
        // 检查是否需要保存日志明细
        boolean shouldFlushLogs = logDetailBuffer.size() >= BATCH_SAVE_SIZE;
        if (shouldFlushLogs) {
            importLogDetailRepo.saveAll(logDetailBuffer);
            entityManager.flush();
            entityManager.clear();
            logDetailBuffer.clear();
        }

        // 更新进度（使用轻量级方法，通过 selfProvider 确保 @Transactional 生效）
        selfProvider.getObject().flushProgressIfNeeded(recordId, total, success, processed, failLogEntries,
                importLogEntries);
    }

    /**
     * 序列化原始数据为 JSON 字符串
     */
    private String serializeRawData(Map<String, String> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rawData);
        } catch (JsonProcessingException e) {
            log.warn("[Import] 序列化原始数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新进度快照（含CLOB字段）- 已弃用，仅用于导入结束时的最终更新
     * 导入过程中的进度更新请使用轻量级的 flushProgressIfNeeded + updateProgressCounters
     *
     * @deprecated 性能优化后，CLOB字段仅在导入结束时写入一次，过程中使用 updateProgressCounters
     */
    @Deprecated(forRemoval = false)
    @Transactional
    protected void updateProgressSnapshot(String recordId,
            int total,
            int success,
            int fail,
            String failLogs,
            String importLogs) {
        importRecordRepo.findById(recordId).ifPresent(record -> {
            if (STATUS_DELETING.equals(record.getStatus()) || STATUS_ROLLED_BACK.equals(record.getStatus())) {
                return;
            }
            LocalDateTime heartbeatAt = LocalDateTime.now();
            record.setTotalCount(total);
            record.setSuccessCount(success);
            record.setFailCount(fail);
            record.setFailLogs(failLogs);
            record.setImportLogs(importLogs);
            // Heartbeat uses createdAt as activity timestamp for timeout checks.
            record.setCreatedAt(heartbeatAt);
            importRecordRepo.save(record);
        });
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
        if (logs.isEmpty())
            return "[]";
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
                .collect(Collectors.groupingBy(result -> normalizeOrderNumber(result.getDto().getOrderNumber()),
                        LinkedHashMap::new, Collectors.toList()));

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
                log.warn("[Import] 创建退货单失败: fileName={}, orderNumber={}, error={}", sourceFileName, orderNumber, errMsg,
                        e);
                orderErrors.put(orderNumber, errMsg);
            }
        }

        return new ReturnOrderPreparation(createdOrders, orderErrors, orderCreatedFlags);
    }

    @Transactional
    public ImportRecordDTO requestDeleteImportedData(String importId) {
        transitionProcessingToTimeoutIfNeeded();

        ImportRecord record = importRecordRepo.findById(importId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Import record not found: " + importId));

        if (STATUS_ROLLED_BACK.equals(record.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "本次导入数据已删除，无需重复操作");
        }
        if (STATUS_DELETING.equals(record.getStatus())) {
            // Retry dispatch in case previous async worker was interrupted.
            selfProvider.getObject().processDeleteImportedDataAsync(importId);
            return toDTO(record, false);
        }
        if (!STATUS_COMPLETED.equals(record.getStatus()) && !STATUS_TIMEOUT.equals(record.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅已结束或已超时的导入记录可执行删除");
        }

        record.setStatus(STATUS_DELETING);
        importRecordRepo.save(record);

        // Invoke through Spring proxy so @Async is effective.
        selfProvider.getObject().processDeleteImportedDataAsync(importId);
        return toDTO(record, false);
    }

    @Async("importTaskExecutor")
    public void processDeleteImportedDataAsync(String importId) {
        log.info("[Import] [{}] 异步删除任务启动", importId);
        try {
            selfProvider.getObject().executeDeleteImportedData(importId);
            log.info("[Import] [{}] 删除导入数据完成", importId);
        } catch (Exception e) {
            log.error("[Import] [{}] 删除导入数据失败: {}", importId, e.getMessage(), e);
            markDeleteFailed(importId, e.getMessage() != null ? e.getMessage() : "删除导入数据失败");
        }
    }

    @Transactional
    public void executeDeleteImportedData(String importId) {
        log.info("[Import] [{}] 开始执行删除导入数据", importId);

        ImportRecord record = importRecordRepo.findById(importId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Import record not found: " + importId));

        log.info("[Import] [{}] 导入记录类型: {}, importLogs长度: {}",
                importId, record.getImportType(),
                record.getImportLogs() != null ? record.getImportLogs().length() : 0);

        List<Map<String, Object>> rows = deserializeLogs(record.getImportLogs());
        log.info("[Import] [{}] 解析到 {} 条日志记录", importId, rows.size());

        List<Map<String, Object>> successRows = rows.stream()
                .filter(row -> "success".equalsIgnoreCase(String.valueOf(row.getOrDefault("status", ""))))
                .collect(Collectors.toList());

        log.info("[Import] [{}] 其中成功记录 {} 条", importId, successRows.size());

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
            List<String> allPartIds = new ArrayList<>(partIdsToDelete);

            // Remove child rows first to avoid FK violations on APMS_PART delete.
            for (List<String> batch : partitionList(allPartIds, DELETE_BATCH_SIZE)) {
                analysisReportRepository.deleteByPartIdIn(batch);
                ocrTaskRepository.deleteByPartIdIn(batch);
            }
            entityManager.flush();

            List<String> existingPartIds = partRepository.findAllById(partIdsToDelete).stream()
                    .map(Part::getId)
                    .collect(Collectors.toList());
            for (List<String> batch : partitionList(existingPartIds, DELETE_BATCH_SIZE)) {
                partRepository.deleteAllByIdInBatch(batch);
                deletedPartCount += batch.size();
            }
            entityManager.flush();
        }

        int deletedOrderCount = 0;
        int skippedOrderCount = 0;
        if (!orderIdsToDelete.isEmpty()) {
            // APMS_ANALYSIS_ORDER has FK to APMS_RETURN_ORDER.ORDER_ID.
            for (List<String> batch : partitionList(new ArrayList<>(orderIdsToDelete), DELETE_BATCH_SIZE)) {
                analysisOrderRepository.deleteByOrderIdIn(batch);
            }
            entityManager.flush();
        }
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
        log.info("[Import] [{}] 删除导入数据成功完成，状态已更新为 rolled_back", importId);
    }

    private <T> List<List<T>> partitionList(List<T> source, int batchSize) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<List<T>> partitions = new ArrayList<>();
        for (int start = 0; start < source.size(); start += batchSize) {
            int end = Math.min(start + batchSize, source.size());
            partitions.add(source.subList(start, end));
        }
        return partitions;
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
                .filter(value -> !isPlaceholderValue(value))
                .findFirst()
                .orElse(null);
    }

    private boolean isPlaceholderValue(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty()
                || "N/A".equals(normalized)
                || "NA".equals(normalized)
                || "NULL".equals(normalized)
                || "-".equals(normalized);
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

    private String escapeForLog(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 32 && ch <= 126) {
                sb.append(ch);
            } else {
                sb.append(String.format("\\u%04x", (int) ch));
            }
        }
        return sb.toString();
    }

    private LocalDateTime resolveImportCreatedAt(String sourceFileName, Map<String, String> rawData) {
        Integer year = extractYearFromFileName(sourceFileName);
        Integer month = extractMonth(rawData);
        if (year == null || month == null) {
            return null;
        }
        return LocalDate.of(year, month, 1).atStartOfDay();
    }

    private Integer extractYearFromFileName(String sourceFileName) {
        if (sourceFileName == null || sourceFileName.isBlank()) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(sourceFileName);
        Integer year = null;
        while (matcher.find()) {
            year = Integer.parseInt(matcher.group(1));
        }
        return year;
    }

    private Integer extractMonth(Map<String, String> rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }

        for (String key : MONTH_FIELD_KEYS) {
            Integer parsed = parseMonth(rawData.get(key));
            if (parsed != null) {
                return parsed;
            }
        }

        for (String value : rawData.values()) {
            Integer parsed = parseMonth(value);
            if (parsed != null) {
                return parsed;
            }
        }

        return null;
    }

    private Integer parseMonth(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isBlank() || isPlaceholderValue(value)) {
            return null;
        }

        Matcher monthTextMatcher = MONTH_TEXT_PATTERN.matcher(value);
        if (monthTextMatcher.find()) {
            return normalizeMonth(Integer.parseInt(monthTextMatcher.group(1)));
        }

        if (value.matches("\\d{1,2}")) {
            return normalizeMonth(Integer.parseInt(value));
        }

        if (value.matches("\\d{1,2}\\.0")) {
            return normalizeMonth((int) Double.parseDouble(value));
        }

        for (DateTimeFormatter formatter : MONTH_DATE_FORMATTERS) {
            try {
                return normalizeMonth(LocalDate.parse(value, formatter).getMonthValue());
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private Integer normalizeMonth(Integer month) {
        if (month == null || month < 1 || month > 12) {
            return null;
        }
        return month;
    }

    private String classifyErrorCode(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "UNKNOWN_ERROR";
        }
        String msg = errorMessage.toLowerCase();
        if (msg.contains("order") && msg.contains("not found"))
            return "ORDER_NOT_FOUND";
        if (msg.contains("退货单号不存在"))
            return "ORDER_NOT_FOUND";
        if (msg.contains("退货单号已存在"))
            return "DUPLICATE_ORDER_NUMBER";
        if (msg.contains("零件号主数据不存在") || msg.contains("未映射事业部") || msg.contains("未映射产品平台"))
            return "PART_CODE_MAPPING_NOT_FOUND";
        if (msg.contains("不能为空") || msg.contains("required"))
            return "REQUIRED_FIELD_MISSING";
        if (msg.contains("日期") && msg.contains("解析"))
            return "INVALID_DATE_FORMAT";
        if (msg.contains("数字") && msg.contains("解析"))
            return "INVALID_NUMBER_FORMAT";
        if (msg.contains("文件解析失败"))
            return "FILE_PARSE_FAILED";
        if (msg.contains("读取文件失败"))
            return "FILE_READ_FAILED";
        return "IMPORT_ROW_FAILED";
    }

    private String resolveImportErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }

        if (throwable instanceof ResponseStatusException responseStatusException) {
            String reason = responseStatusException.getReason();
            if (reason != null && !reason.isBlank()) {
                return enhanceRequiredFieldMessage(reason);
            }
        }

        String directMessage = throwable.getMessage();
        if (directMessage != null && !directMessage.isBlank()) {
            String enhanced = enhanceRequiredFieldMessage(directMessage);
            if (enhanced != null) {
                return enhanced;
            }
        }

        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String rootMessage = root.getMessage();
        if (rootMessage != null && !rootMessage.isBlank()) {
            String enhanced = enhanceRequiredFieldMessage(rootMessage);
            if (enhanced != null) {
                return enhanced;
            }
            return rootMessage;
        }

        return throwable.getClass().getSimpleName();
    }

    private String enhanceRequiredFieldMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String fieldKey = extractMissingFieldKey(message);
        if (fieldKey != null) {
            return "必填字段缺失: " + toFieldDisplayName(fieldKey);
        }

        String msgLower = message.toLowerCase(Locale.ROOT);
        if (message.contains("不能为空") || msgLower.contains("required")) {
            return message;
        }
        return null;
    }

    private String extractMissingFieldKey(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        Matcher hibernateMatcher = Pattern
                .compile("not-null property references a null or transient value\\s*:\\s*[\\w.$]+\\.([\\w$]+)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(message);
        if (hibernateMatcher.find()) {
            return normalizeFieldKey(hibernateMatcher.group(1));
        }

        Matcher oracleMatcher = Pattern
                .compile("ORA-01400:.*?\\(.*?\\.\\\"([A-Z0-9_]+)\\\"\\)", Pattern.CASE_INSENSITIVE)
                .matcher(message);
        if (oracleMatcher.find()) {
            return normalizeFieldKey(oracleMatcher.group(1));
        }

        Matcher cannotInsertMatcher = Pattern
                .compile("cannot insert null into\\s*\\(.*?\\.\\\"([A-Z0-9_]+)\\\"\\)", Pattern.CASE_INSENSITIVE)
                .matcher(message);
        if (cannotInsertMatcher.find()) {
            return normalizeFieldKey(cannotInsertMatcher.group(1));
        }

        Matcher chineseRequiredMatcher = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9_/-]+)不能为空")
                .matcher(message);
        if (chineseRequiredMatcher.find()) {
            return normalizeFieldKey(chineseRequiredMatcher.group(1));
        }

        Matcher englishRequiredMatcher = Pattern
                .compile("([A-Za-z][A-Za-z0-9_ ]+) is required", Pattern.CASE_INSENSITIVE)
                .matcher(message);
        if (englishRequiredMatcher.find()) {
            return normalizeFieldKey(englishRequiredMatcher.group(1));
        }

        return null;
    }

    private String normalizeFieldKey(String rawField) {
        if (rawField == null || rawField.isBlank()) {
            return null;
        }

        String trimmed = rawField.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "ORDER_NUMBER", "退货单号", "ORDERNUMBER" -> "orderNumber";
            case "PART_CODE", "博世零件号", "PARTCODE" -> "partCode";
            case "ANALYST" -> "analyst";
            case "VEHICLE_VIN", "VIN", "VIN码", "车架号", "底盘号" -> "vehicleVIN";
            case "RECEIVE_DATE", "收货时间" -> "receiveDate";
            case "COMPLAINT_DATE" -> "complaintDate";
            case "TRACKING_NUMBER", "快递单号" -> "trackingNumber";
            case "CUSTOMER_ID", "客户", "CUSTOMER" -> "customerId";
            default -> {
                if (trimmed.contains(".")) {
                    String suffix = trimmed.substring(trimmed.lastIndexOf('.') + 1);
                    yield normalizeFieldKey(suffix);
                }
                String compact = trimmed.replace(" ", "");
                if ("Analyst".equalsIgnoreCase(compact)) {
                    yield "analyst";
                }
                yield compact;
            }
        };
    }

    private String toFieldDisplayName(String fieldKey) {
        return switch (fieldKey) {
            case "orderNumber" -> "退货单号(orderNumber)";
            case "partCode" -> "博世零件号(partCode)";
            case "analyst" -> "分析人员(analyst)";
            case "vehicleVIN" -> "VIN码(vehicleVIN)";
            case "receiveDate" -> "收货时间(receiveDate)";
            case "complaintDate" -> "客诉日期(complaintDate)";
            case "trackingNumber" -> "快递单号(trackingNumber)";
            case "customerId" -> "客户(customerId)";
            default -> fieldKey;
        };
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
            return objectMapper.readValue(importLogs, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[Import] 反序列化 importLogs 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private record ReturnOrderPreparation(
            Map<String, ReturnOrderDTO> createdOrders,
            Map<String, String> orderErrors,
            Map<String, Boolean> orderCreatedFlags) {
    }

    private record PendingPartImport(
            PartImportParser.ParseResult result,
            String fileName,
            ReturnOrderDTO createdOrder,
            boolean orderCreated) {
    }
}
