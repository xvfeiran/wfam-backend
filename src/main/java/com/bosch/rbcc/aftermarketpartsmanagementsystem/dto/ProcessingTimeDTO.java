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
@Schema(description = "阶段处理时间")
public class ProcessingTimeDTO {
    @Schema(description = "处理阶段", example = "初分析")
    private String stage;
    @Schema(description = "平均处理天数", example = "2.5")
    private double avgDays;
}
