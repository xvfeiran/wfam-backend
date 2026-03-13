package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
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
    @Schema(description = "客户ID", example = "1")
    private String customerId;
    @Schema(description = "客户", example = "一汽大众")
    private String customer;
    @Schema(description = "收货日期", example = "2024-01-15")
    private String receiveDate;
    @Schema(description = "投诉日期", example = "2024-01-10")
    private String complaintDate;
    @Schema(description = "退回方式", example = "express", allowableValues = {"express", "pickup"})
    @Pattern(regexp = "^(express|pickup)$", message = "退回方式只能是快递或自提")
    private String returnMethod;
    @Schema(description = "物流单号", example = "SF1234567890")
    @Size(max = 50, message = "快递单号长度不能超过50个字符")
    private String trackingNumber;
    @Schema(description = "退货数量", example = "5")
    @Min(value = 1, message = "退货数量不能小于1")
    @Max(value = 9999, message = "退货数量不能大于9999")
    private Integer returnQuantity;

    @Schema(description = "失效类型（BA20代表0km，不能抽样）", example = "BA40")
    private String failureType;

    private Integer initialAnalysisQuantity;
    private Integer detailedAnalysisQuantity;
    private Integer scrappedQuantity;
    private Integer qcCreatedQuantity;
    private Integer qcNotCreatedQuantity;
    @Schema(description = "状态", example = "pending_registration")
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
}
