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
@Schema(description = "附件（保修件）")
public class PartDTO {
    @Schema(description = "附件ID", example = "part-1")
    private String id;
    @Schema(description = "附件编号", example = "WS-WSA-0001")
    private String partNumber;
    @Schema(description = "所属退货单ID", example = "1")
    private String orderId;
    private String orderNumber;
    @Schema(description = "零件码", example = "F00RJ02806")
    private String partCode;
    @Schema(description = "事业部", example = "WS")
    private String businessUnit;
    @Schema(description = "产品平台", example = "WSA")
    private String productPlatform;
    private String productionShift;
    @Schema(description = "退货类型（BA代码）", example = "BA40")
    private String complaintType;
    @Schema(description = "客户失效类型", example = "噪音")
    private String failureType;
    private String repairStation;
    private String complaintLocation;
    private String responsibleEngineer;
    private String analyst;
    private String qcNo;
    private String vehicleProductionDate;
    private String vehiclePurchaseDate;
    private String vehicleFailureDate;
    private String vehicleVIN;
    private Integer vehicleMileage;
    private String customerDescription;
    private String otherDescription;
    @Schema(description = "状态", example = "registered")
    private String status;
    private List<String> images;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
}
