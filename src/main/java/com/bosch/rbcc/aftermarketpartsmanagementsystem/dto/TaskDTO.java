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
@Schema(description = "任务中心项")
public class TaskDTO {
    @Schema(description = "任务ID", example = "1")
    private String id;
    @Schema(description = "任务类型", example = "initial_analysis")
    private String type;
    @Schema(description = "任务标题", example = "初分析待处理")
    private String title;
    @Schema(description = "数量", example = "3")
    private Integer count;
    @Schema(description = "优先级", example = "high")
    private String priority;
}
