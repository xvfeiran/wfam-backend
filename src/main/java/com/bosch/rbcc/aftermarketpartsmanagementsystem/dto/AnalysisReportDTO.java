package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分析报告")
public class AnalysisReportDTO {
    @Schema(description = "报告ID", example = "report-1")
    private String id;
    @Schema(description = "关联附件ID", example = "part-1")
    private String partId;
    @Schema(description = "使用模板ID", example = "template-1")
    private String templateId;
    private Map<String, Object> content;
    @Schema(description = "报告状态", example = "submitted")
    private String status;
    @Schema(description = "报告摘要")
    private String summary;
    @Schema(description = "责任判定", example = "B")
    private String responsibility;
    private List<String> attachments;
    private String submittedBy;
    private String submittedAt;
    private String approvedBy;
    private String approvedAt;
    private String createdBy;
    private String createdAt;
}
