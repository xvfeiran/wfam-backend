package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "报废申请")
public class ScrapApplicationDTO {
    @Schema(description = "申请ID", example = "scrap-1")
    private String id;
    @Schema(description = "退货单号", example = "RO-2024-001")
    private String orderNumber;
    @Schema(description = "附件编号列表", example = "WS-WSA-0001, WS-WSA-0002")
    private String partNumbers;
    @Schema(description = "报废数量", example = "3")
    private int quantity;
    private String applicant;
    private String approver;
    private String applyTime;
    private String approveTime;
    @Schema(description = "申请状态", example = "pending")
    private String status;
    private String reason;
    private String rejectReason;
}
