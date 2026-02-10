package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.dashboard;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.DashboardStatsDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TaskDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TrendDataPointDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "仪表盘", description = "仪表盘统计数据、任务中心、趋势图")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MockDataProvider mockData;

    @Operation(summary = "获取仪表盘统计数据")
    @GetMapping("/stats")
    public DashboardStatsDTO getStats() {
        return mockData.getDashboardStats();
    }

    @Operation(summary = "获取任务中心列表")
    @GetMapping("/tasks")
    public List<TaskDTO> getTasks() {
        return mockData.getTasks();
    }

    @Operation(summary = "获取趋势数据")
    @GetMapping("/trend")
    public List<TrendDataPointDTO> getTrend(@Parameter(description = "查询天数，默认30天") @RequestParam(defaultValue = "30") int days) {
        return mockData.generateTrendData(days);
    }
}
