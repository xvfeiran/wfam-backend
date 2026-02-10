package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.dashboard;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.DashboardStatsDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TaskDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TrendDataPointDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final MockDataProvider mockData;

    @GetMapping("/stats")
    public DashboardStatsDTO getStats() {
        return mockData.getDashboardStats();
    }

    @GetMapping("/tasks")
    public List<TaskDTO> getTasks() {
        return mockData.getTasks();
    }

    @GetMapping("/trend")
    public List<TrendDataPointDTO> getTrend(@RequestParam(defaultValue = "30") int days) {
        return mockData.generateTrendData(days);
    }
}
