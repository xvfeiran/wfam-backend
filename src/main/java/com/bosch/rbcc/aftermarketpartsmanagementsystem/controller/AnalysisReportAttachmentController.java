package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImageUploadResult;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.AnalysisReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/analysis-reports/{reportId}/attachments")
@RequiredArgsConstructor
@Tag(name = "精分析报告附件", description = "精分析报告附件上传与管理")
public class AnalysisReportAttachmentController {

    private final AnalysisReportService analysisReportService;

    @PostMapping
    @Operation(summary = "上传附件", description = "上传一张精分析附件图片")
    public ImageUploadResult uploadAttachment(
            @PathVariable String reportId,
            @RequestParam("file") MultipartFile file) {
        return analysisReportService.uploadAttachment(reportId, file);
    }

    @DeleteMapping("/{attachmentId:.+}")
    @Operation(summary = "删除附件", description = "删除指定精分析附件")
    public void deleteAttachment(
            @PathVariable String reportId,
            @PathVariable String attachmentId) {
        String relativePath = "analysis/" + reportId + "/" + attachmentId;
        analysisReportService.deleteAttachment(reportId, relativePath);
    }
}
