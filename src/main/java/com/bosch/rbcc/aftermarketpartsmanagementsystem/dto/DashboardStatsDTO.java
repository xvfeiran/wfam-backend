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
@Schema(description = "仪表盘统计数据")
public class DashboardStatsDTO {
    @Schema(description = "退货单总数", example = "15")
    private Integer totalOrders;
    @Schema(description = "附件总数", example = "42")
    private Integer totalParts;
    @Schema(description = "待处理任务", example = "8")
    private Integer pendingTasks;
    @Schema(description = "完成率", example = "68.5")
    private double completionRate;
}
