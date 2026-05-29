package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "业务单元不能为空")
    private String businessUnit;
    @Schema(description = "产品平台", example = "WSA")
    @NotBlank(message = "产品平台不能为空")
    private String productPlatform;
    private String partProductionDate;
    private String productionShift;
    @Schema(description = "客户失效类型（NVH/功能/外观）", example = "NVH")
    private String failureType;
    @Schema(description = "博世失效类型", example = "BA40")
    private String boschFailureType;
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
    @Schema(description = "其他信息", example = "备注说明")
    @Size(max = 500, message = "其他信息长度不能超过500个字符")
    private String otherInfo;
    @Schema(description = "状态", example = "registered")
    private String status;
    private List<String> images;
    @Schema(description = "是否抽样 (0=未抽样, 1=已抽样)", example = "0")
    private Integer isSample;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
}
