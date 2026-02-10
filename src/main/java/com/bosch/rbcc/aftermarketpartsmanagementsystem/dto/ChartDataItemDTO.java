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
@Schema(description = "图表数据项")
public class ChartDataItemDTO {
    @Schema(description = "名称", example = "一汽大众")
    private String name;
    @Schema(description = "数值", example = "45")
    private Number value;
}
