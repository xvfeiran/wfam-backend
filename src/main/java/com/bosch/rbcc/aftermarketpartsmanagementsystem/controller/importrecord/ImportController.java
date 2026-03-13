package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.importrecord;

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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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

    @Operation(summary = "查询单条导入记录（用于轮询状态）")
    @GetMapping("/{id}")
    public ImportRecordDTO getById(@PathVariable String id) {
        log.debug("[ImportController] 查询导入记录: id={}", id);
        return importService.getById(id);
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
