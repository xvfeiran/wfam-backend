package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分析报告审批申请")
public class AnalysisApplicationDTO {
    @Schema(description = "申请ID", example = "analysis-1")
    private String id;
    @Schema(description = "报告编号", example = "RPT-2024-001")
    private String reportNumber;
    @Schema(description = "附件编号", example = "WS-WSA-0001")
    private String partNumber;
    @Schema(description = "产品平台", example = "WSA")
    private String productPlatform;
    @Schema(description = "失效类型", example = "噪音")
    private String failureType;
    private String submitter;
    private String approver;
    private String submitTime;
    private String approveTime;
    @Schema(description = "申请状态", example = "pending")
    private String status;
    private String summary;
    private String templateId;
    private Map<String, Object> content;
}
