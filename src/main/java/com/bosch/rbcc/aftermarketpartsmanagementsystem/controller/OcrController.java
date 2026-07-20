package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.OcrTaskDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.FileStorageService;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * OCR 任务控制器
 * <p>
 * POST /api/v1/ocr/tasks    — 上传图片，创建 OCR 任务（立即返回 taskId）
 * GET  /api/v1/ocr/tasks/{taskId} — 轮询任务状态（前端每 3s 调用一次）
 */
@RestController
@RequestMapping("/api/v1/ocr")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "OCR Recognition", description = "客诉信息卡 OCR 识别接口")
public class OcrController {

    private final OcrService ocrService;
    private final FileStorageService fileStorageService;

    /**
     * 创建 OCR 任务。
     * 上传图片后立即返回 taskId，实际识别在后台异步执行（约 30s）。
     *
     * @param file   图片文件（jpg/png，≤10MB）
     * @param partId 关联的 Part ID（编辑模式必传；新建模式可省略）
     */
    @PostMapping("/tasks")
    @Operation(summary = "创建 OCR 任务", description = "上传图片，后台异步识别，返回 taskId 供前端轮询")
    public OcrTaskDTO createTask(
            @Parameter(description = "客诉信息卡图片", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "关联 Part ID（编辑模式传入）")
            @RequestParam(value = "partId", required = false) String partId) {

        log.info("收到 OCR 上传请求: file={}, size={}KB, partId={}",
                file.getOriginalFilename(), file.getSize() / 1024, partId);

        return ocrService.createTask(file, partId);
    }

    /**
     * 轮询 OCR 任务状态。
     * status: CREATED → PROCESSING → SUCCESS | FAILED
     */
    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询 OCR 任务状态", description = "前端每 3s 调用，直到 status 为 SUCCESS 或 FAILED")
    public OcrTaskDTO getTask(
            @Parameter(description = "任务 ID", required = true)
            @PathVariable String taskId) {

        return ocrService.getTask(taskId);
    }

    @GetMapping("/tasks/latest")
    @Operation(summary = "查询指定售后件最近 OCR 任务", description = "编辑页回显 OCR 图片与识别结果")
    public OcrTaskDTO getLatestTaskByPartId(
            @Parameter(description = "Part ID", required = true)
            @RequestParam String partId) {
        return ocrService.getLatestTaskByPartId(partId);
    }

    @GetMapping("/tasks/{taskId}/image")
    @Operation(summary = "获取 OCR 任务图片", description = "直接以图片形式返回 OCR 原图")
    public ResponseEntity<Resource> getTaskImage(
            @Parameter(description = "任务 ID", required = true)
            @PathVariable String taskId) {
        String relativePath = ocrService.getTaskImageRelativePath(taskId);
        int slashIdx = relativePath.indexOf('/');
        String category = slashIdx >= 0 ? relativePath.substring(0, slashIdx) : "pending";
        String fileName = slashIdx >= 0 ? relativePath.substring(slashIdx + 1) : relativePath;
        Resource resource = fileStorageService.load(category, fileName);
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OCR 图片不存在: " + relativePath);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(inferImageContentType(fileName)))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(resource.getFilename()).build().toString())
                .body(resource);
    }

    private String inferImageContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    @PostMapping("/tasks/{taskId}/retry")
    @Operation(summary = "重试 OCR 任务", description = "基于原图重新发起识别，适用于失败任务重试")
    public OcrTaskDTO retryTask(
            @Parameter(description = "任务 ID", required = true)
            @PathVariable String taskId) {
        return ocrService.retryTask(taskId);
    }
}
