package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImportRecordDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ImportRecord;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ImportRecordRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel.ReturnOrderImportParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED  = "completed";
    public static final String STATUS_FAILED     = "failed";

    private static final String TYPE_RETURN_ORDER = "return_order";

    private final ReturnOrderImportParser returnOrderImportParser;
    private final ReturnOrderService returnOrderService;
    private final ImportRecordRepository importRecordRepo;
    private final ObjectMapper objectMapper;

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
        return toDTO(record);
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
    // 3. 查询单条（前端轮询用）
    // ─────────────────────────────────────────────
    public ImportRecordDTO getById(String id) {
        return importRecordRepo.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import record not found: " + id));
    }

    // ─────────────────────────────────────────────
    // 4. 列表查询
    // ─────────────────────────────────────────────
    public Page<ImportRecordDTO> listImports(String type, Pageable pageable) {
        Page<ImportRecord> page = importRecordRepo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("importType"), type));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        List<ImportRecordDTO> dtos = page.getContent().stream()
                .map(this::toDTO)
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
            record.setStatus(STATUS_FAILED);
            record.setFailLogs(serialize(List.of(Map.of("row", 0, "error", errorMessage))));
            importRecordRepo.save(record);
            log.info("[Import] [{}] 记录已更新为 failed", recordId);
        }, () -> log.error("[Import] [{}] 找不到记录，无法标记为 failed", recordId));
    }

    private ImportRecordDTO toDTO(ImportRecord record) {
        return ImportRecordDTO.builder()
                .id(record.getId())
                .importType(record.getImportType())
                .fileName(record.getFileName())
                .status(record.getStatus())
                .totalCount(record.getTotalCount())
                .successCount(record.getSuccessCount())
                .failCount(record.getFailCount())
                .failLogs(record.getFailLogs())
                .importLogs(record.getImportLogs())
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
}
