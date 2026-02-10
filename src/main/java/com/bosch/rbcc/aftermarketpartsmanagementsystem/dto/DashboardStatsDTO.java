package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private int totalOrders;
    private int totalParts;
    private int pendingTasks;
    private double completionRate;
}
