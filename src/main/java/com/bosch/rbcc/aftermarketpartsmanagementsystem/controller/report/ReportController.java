package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.report;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.*;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final MockDataProvider mockData;

    @GetMapping("/trend")
    public List<TrendDataPointDTO> getTrend(@RequestParam(defaultValue = "30") int days) {
        return mockData.generateTrendData(days);
    }

    @GetMapping("/customer-ranking")
    public List<ChartDataItemDTO> getCustomerRanking() {
        return mockData.getCustomerRanking();
    }

    @GetMapping("/failure-mode-distribution")
    public List<ChartDataItemDTO> getFailureModeDistribution() {
        return mockData.getFailureModeData();
    }

    @GetMapping("/bu-distribution")
    public List<ChartDataItemDTO> getBuDistribution() {
        return mockData.getBuDistribution();
    }

    @GetMapping("/processing-time")
    public List<ProcessingTimeDTO> getProcessingTime() {
        return mockData.getProcessingTimeData();
    }

    @GetMapping("/templates")
    public List<ReportTemplateDTO> getTemplates() {
        return mockData.getTemplates();
    }

    @PostMapping("/analysis")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisReportDTO submitReport(@RequestBody AnalysisReportDTO dto) {
        return dto;
    }
}
