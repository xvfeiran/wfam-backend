package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCR识别结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OCR识别结果")
public class OcrResultDTO {

    @Schema(description = "汽车生产日期", example = "2025-06-15")
    private String vehicleProductionDate;

    @Schema(description = "汽车购买日期", example = "2025-07-20")
    private String vehiclePurchaseDate;

    @Schema(description = "汽车失效日期", example = "2026-01-10")
    private String vehicleFailureDate;

    @Schema(description = "车辆VIN码", example = "LSVAB2183E2123456")
    private String vehicleVIN;

    @Schema(description = "车辆行驶里程(km)", example = "15234")
    private Integer vehicleMileage;

    @Schema(description = "客户失效描述", example = "发动机异响，怠速不稳")
    private String customerDescription;
}
