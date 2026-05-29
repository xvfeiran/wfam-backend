package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分析单")
public class AnalysisOrderDTO {
    @Schema(description = "分析单ID")
    private String id;

    @Schema(description = "关联退货单ID")
    private String orderId;

    @Schema(description = "关联退货单号")
    private String orderNumber;

    @Schema(description = "分析师")
    private String analyst;

    @Schema(description = "状态", example = "pending_sampling")
    private String status;

    @Schema(description = "WorkON报废单号")
    private String workonScrapNo;

    private String statusChangedAt;

    @Schema(description = "关联售后件列表")
    private List<PartDTO> parts;

    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
}
