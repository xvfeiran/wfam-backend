package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.importrecord;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImportFileSummaryDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImportRecordDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PageResponse;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "导入管理", description = "Excel 导入及导入记录查询")
@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @Operation(
        summary = "导入退货单（新格式，异步）",
        description = "立即返回 status=processing 的记录；后台异步处理文件；通过 GET /imports/{id} 轮询结果"
    )
    @PostMapping("/return-orders")
    public ImportRecordDTO importReturnOrders(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.xlsx";
        log.info("[ImportController] 收到导入请求: fileName={}, size={} bytes", fileName, file.getSize());

        // 1. 立即读取文件字节（请求结束后流不可用）
        byte[] fileBytes = file.getBytes();
        log.info("[ImportController] 文件字节已读取: {} bytes", fileBytes.length);

        // 2. 同步创建 processing 记录，立即返回给前端
        ImportRecordDTO record = importService.createPendingRecord(fileName);
        log.info("[ImportController] 返回 processing 记录: id={}", record.getId());

        // 3. 触发异步处理（通过 Spring 代理，@Async 生效）
        importService.processReturnOrdersAsync(record.getId(), fileBytes);

        return record;
    }

    @Operation(
        summary = "导入售后件（异步）",
        description = "立即返回 status=processing 的记录；后台异步处理文件；通过 GET /imports/{id} 轮询结果"
    )
    @PostMapping("/parts")
    public ImportRecordDTO importParts(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.xlsx";
        log.info("[ImportController] 收到售后件导入请求: fileName={}, size={} bytes", fileName, file.getSize());

        byte[] fileBytes = file.getBytes();

        ImportRecordDTO record = importService.createPendingPartRecord(fileName);
        log.info("[ImportController] 返回售后件导入 processing 记录: id={}", record.getId());

        importService.processPartsAsync(record.getId(), fileBytes);

        return record;
    }

    @Operation(
        summary = "按文件夹导入售后件（递归，异步）",
        description = "传入后端可访问的目录路径；递归扫描子文件夹内的 .xls/.xlsx；通过 GET /imports/{id} 轮询结果"
    )
    @PostMapping("/parts/folder")
    public ImportRecordDTO importPartsFromFolder(@RequestBody PartFolderImportRequest request) {
        if (request == null || request.getFolderPath() == null || request.getFolderPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "folderPath is required");
        }

        String folderPath = request.getFolderPath().trim();
        log.info("[ImportController] Received part folder import request: folderPath={}", escapeForLog(folderPath));

        ImportRecordDTO record = importService.createPendingPartRecord("[FOLDER] " + folderPath);
        log.info("[ImportController] Created processing import record: id={}", record.getId());

        importService.processPartsFolderAsync(record.getId(), folderPath);
        return record;
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

    @Operation(summary = "查询单条导入记录（用于轮询状态）")
    @GetMapping("/{id}")
    public ImportRecordDTO getById(@PathVariable String id) {
        log.debug("[ImportController] 查询导入记录: id={}", id);
        return importService.getById(id);
    }

    @Operation(summary = "查询导入详情中的文件汇总列表")
    @GetMapping("/{id}/files")
    public List<ImportFileSummaryDTO> listFiles(@PathVariable String id) {
        return importService.listImportFiles(id);
    }

    @Operation(summary = "按文件名分页查询导入日志")
    @GetMapping("/{id}/logs")
    public PageResponse<Map<String, Object>> listLogsByFile(
            @PathVariable String id,
            @RequestParam String fileName,
            @PageableDefault(size = 200) Pageable pageable) {
        return PageResponse.of(importService.listImportLogsByFile(id, fileName, pageable));
    }

    @Operation(summary = "分页查询本次导入的全部错误日志")
    @GetMapping("/{id}/errors")
    public PageResponse<Map<String, Object>> listErrors(
            @PathVariable String id,
            @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.of(importService.listImportErrors(id, pageable));
    }

    @Operation(summary = "删除本次导入产生的数据")
    @DeleteMapping("/{id}/records")
    public ImportRecordDTO deleteImportedData(@PathVariable String id) {
        return importService.requestDeleteImportedData(id);
    }

    @Operation(summary = "查询导入记录列表")
    @GetMapping
    public PageResponse<ImportRecordDTO> list(
            @Parameter(description = "导入类型（return_order / part）") @RequestParam(required = false) String type,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ImportRecordDTO> page = importService.listImports(type, pageable);
        return PageResponse.of(page);
    }
}
