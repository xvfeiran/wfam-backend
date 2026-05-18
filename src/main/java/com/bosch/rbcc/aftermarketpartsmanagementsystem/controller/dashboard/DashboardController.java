package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.dashboard;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.DashboardStatsDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TaskDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TrendDataPointDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaders;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.DashboardMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Tag(name = "仪表盘", description = "仪表盘统计数据、任务中心、趋势图")
@RestController
@RequestMapping("/api/v1/dashboard")
@Slf4j
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardMetricsService dashboardMetricsService;

    @Operation(summary = "获取仪表盘统计数据")
    @GetMapping("/stats")
    public DashboardStatsDTO getStats() {
        String analyst = getCurrentAnalyst();
        String roleNames = getCurrentRoleNames();
        return callWithTimeout(
            () -> dashboardMetricsService.getDashboardStats(analyst, roleNames),
            Duration.ofSeconds(3),
            DashboardStatsDTO.builder()
                .totalOrders(0)
                .totalParts(0)
                .pendingTasks(0)
                .completionRate(0.0)
                .build(),
            "dashboard.stats"
        );
    }

    @Operation(summary = "获取任务中心列表")
    @GetMapping("/tasks")
    public List<TaskDTO> getTasks() {
        String analyst = getCurrentAnalyst();
        String roleNames = getCurrentRoleNames();
        return callWithTimeout(
            () -> dashboardMetricsService.getTasks(analyst, roleNames),
            Duration.ofSeconds(3),
            List.of(),
            "dashboard.tasks"
        );
    }

    @Operation(summary = "获取趋势数据")
    @GetMapping("/trend")
    public List<TrendDataPointDTO> getTrend(@Parameter(description = "查询天数，默认30天") @RequestParam(defaultValue = "30") int days) {
        int safeDays = days <= 0 ? 30 : days;
        return callWithTimeout(
            () -> dashboardMetricsService.getTrend(safeDays),
            Duration.ofSeconds(2),
            emptyTrend(safeDays),
            "dashboard.trend"
        );
    }

    private List<TrendDataPointDTO> emptyTrend(int days) {
        List<TrendDataPointDTO> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            trend.add(TrendDataPointDTO.builder().date(java.time.LocalDate.now().minusDays(i).toString()).orders(0).parts(0).build());
        }
        return trend;
    }

    private <T> T callWithTimeout(Supplier<T> supplier, Duration timeout, T fallback, String apiName) {
        try {
            return CompletableFuture.supplyAsync(supplier::get)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("{} fallback due to {}", apiName, ex.getClass().getSimpleName());
                    return fallback;
                })
                .join();
        } catch (Exception ex) {
            log.warn("{} fallback due to {}", apiName, ex.getClass().getSimpleName());
            return fallback;
        }
    }

    private String getCurrentAnalyst() {
        CommonHeaders headers = CommonHeaderManager.getCommonHeaders();
        return headers != null ? headers.getNtAccount() : null;
    }

    private String getCurrentRoleNames() {
        CommonHeaders headers = CommonHeaderManager.getCommonHeaders();
        return headers != null ? headers.getRoleNames() : null;
    }
}
