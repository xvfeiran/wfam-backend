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
@Schema(description = "退货单")
public class ReturnOrderDTO {
    @Schema(description = "退货单ID", example = "1")
    private String id;
    @Schema(description = "退货单号", example = "RO-2024-001")
    private String orderNumber;
    @Schema(description = "客户", example = "一汽大众")
    private String customer;
    @Schema(description = "收货日期", example = "2024-01-15")
    private String receiveDate;
    @Schema(description = "投诉日期", example = "2024-01-10")
    private String complaintDate;
    @Schema(description = "退回方式", example = "express")
    private String returnMethod;
    @Schema(description = "物流单号", example = "SF1234567890")
    private String trackingNumber;
    @Schema(description = "退货数量", example = "5")
    private Integer returnQuantity;
    private Integer initialAnalysisQuantity;
    private Integer detailedAnalysisQuantity;
    private Integer scrappedQuantity;
    private Integer qcCreatedQuantity;
    private Integer qcNotCreatedQuantity;
    private String description;
    @Schema(description = "状态", example = "pending_registration")
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
}
