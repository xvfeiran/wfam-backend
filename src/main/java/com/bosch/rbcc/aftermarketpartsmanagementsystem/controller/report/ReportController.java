package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.report;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.*;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "报表与分析", description = "统计报表、图表数据、分析模板与报告提交")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final MockDataProvider mockData;

    @Operation(summary = "获取趋势数据")
    @GetMapping("/trend")
    public List<TrendDataPointDTO> getTrend(@Parameter(description = "查询天数，默认30天") @RequestParam(defaultValue = "30") int days) {
        return mockData.generateTrendData(days);
    }

    @Operation(summary = "获取客户退货排行")
    @GetMapping("/customer-ranking")
    public List<ChartDataItemDTO> getCustomerRanking() {
        return mockData.getCustomerRanking();
    }

    @Operation(summary = "获取失效模式分布")
    @GetMapping("/failure-mode-distribution")
    public List<ChartDataItemDTO> getFailureModeDistribution() {
        return mockData.getFailureModeData();
    }

    @Operation(summary = "获取事业部分布")
    @GetMapping("/bu-distribution")
    public List<ChartDataItemDTO> getBuDistribution() {
        return mockData.getBuDistribution();
    }

    @Operation(summary = "获取各阶段平均处理时间")
    @GetMapping("/processing-time")
    public List<ProcessingTimeDTO> getProcessingTime() {
        return mockData.getProcessingTimeData();
    }

    @Operation(summary = "获取所有分析报告模板")
    @GetMapping("/templates")
    public List<ReportTemplateDTO> getTemplates() {
        return mockData.getTemplates();
    }

    @Operation(summary = "提交分析报告")
    @PostMapping("/analysis")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisReportDTO submitReport(@RequestBody AnalysisReportDTO dto) {
        return dto;
    }
}
