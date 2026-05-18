package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ChartDataItemDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.DashboardStatsDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ProcessingTimeDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TaskDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TrendDataPointDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisReport;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisReportRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardMetricsService {

    private static final String PART_STATUS_IN_INITIAL_ANALYSIS = "in_initial_analysis";
    private static final String PART_STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String PART_STATUS_ANALYSIS_COMPLETED = "analysis_completed";
    private static final String PART_STATUS_SCRAPPED = "scrapped";

    private static final String ANALYSIS_ORDER_WORKON_SCRAP_IN_PROGRESS = "workon_scrap_in_progress";

    private final ReturnOrderRepository returnOrderRepository;
    private final PartRepository partRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final AnalysisOrderRepository analysisOrderRepository;
    private final NotificationProperties notificationProperties;

    public DashboardStatsDTO getDashboardStats() {
        int totalOrders = (int) returnOrderRepository.count();
        int totalParts = (int) partRepository.count();

        int completionBase = (int) partRepository.countByStatusIn(List.of(PART_STATUS_ANALYSIS_COMPLETED, PART_STATUS_SCRAPPED));

        double completionRate = totalParts == 0 ? 0.0 : Math.round((completionBase * 10000.0 / totalParts)) / 100.0;

        int pendingTasks = getTasks().stream()
            .map(TaskDTO::getCount)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();

        return DashboardStatsDTO.builder()
            .totalOrders(totalOrders)
            .totalParts(totalParts)
            .pendingTasks(pendingTasks)
            .completionRate(completionRate)
            .build();
    }

    public DashboardStatsDTO getDashboardStats(String analyst, String roleNames) {
        int totalOrders = (int) returnOrderRepository.count();
        int totalParts = (int) partRepository.count();

        int completionBase = (int) partRepository.countByStatusIn(List.of(PART_STATUS_ANALYSIS_COMPLETED, PART_STATUS_SCRAPPED));

        double completionRate = totalParts == 0 ? 0.0 : Math.round((completionBase * 10000.0 / totalParts)) / 100.0;

        int pendingTasks = getTasks(analyst, roleNames).stream()
            .map(TaskDTO::getCount)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();

        return DashboardStatsDTO.builder()
            .totalOrders(totalOrders)
            .totalParts(totalParts)
            .pendingTasks(pendingTasks)
            .completionRate(completionRate)
            .build();
    }

    public List<TaskDTO> getTasks() {
        long initialAnalysisCount = partRepository.countByStatus(PART_STATUS_IN_INITIAL_ANALYSIS);
        long detailedAnalysisCount = partRepository.countByStatus(PART_STATUS_IN_DETAILED_ANALYSIS);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningThreshold = now.minusDays(notificationProperties.getAnalysis().getWarningDays());
        LocalDateTime overdueThreshold = now.minusDays(notificationProperties.getAnalysis().getOverdueDays());
        long warningCount = partRepository.countByStatusAndStatusChangedAtLessThanEqual(PART_STATUS_IN_DETAILED_ANALYSIS, warningThreshold);
        long overdueCount = partRepository.countByStatusAndStatusChangedAtLessThanEqual(PART_STATUS_IN_DETAILED_ANALYSIS, overdueThreshold);

        long approvalCount = analysisReportRepository.countByStatus("submitted");
        long scrapConfirmCount = analysisOrderRepository.countByStatus(ANALYSIS_ORDER_WORKON_SCRAP_IN_PROGRESS);

        List<TaskDTO> tasks = new ArrayList<>();
        tasks.add(task("initial_analysis", "待初分析", initialAnalysisCount, "medium", 1));
        tasks.add(task("detailed_analysis", "待精分析", detailedAnalysisCount, "medium", 2));
        tasks.add(task("warning", "精分析预警", warningCount, "high", 3));
        tasks.add(task("overdue", "精分析超期", overdueCount, "urgent", 4));
        tasks.add(task("approval", "精分析报告待审批", approvalCount, "medium", 5));
        tasks.add(task("scrap_confirm", "报废审批确认", scrapConfirmCount, "medium", 6));
        return tasks;
    }

    public List<TaskDTO> getTasks(String analyst, String roleNames) {
        long initialAnalysisCount = partRepository.countByStatusAndAnalyst(PART_STATUS_IN_INITIAL_ANALYSIS, analyst);
        long detailedAnalysisCount = partRepository.countByStatusAndAnalyst(PART_STATUS_IN_DETAILED_ANALYSIS, analyst);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningThreshold = now.minusDays(notificationProperties.getAnalysis().getWarningDays());
        LocalDateTime overdueThreshold = now.minusDays(notificationProperties.getAnalysis().getOverdueDays());
        long warningCount = partRepository.countByStatusAndAnalystAndStatusChangedAtLessThanEqual(PART_STATUS_IN_DETAILED_ANALYSIS, analyst, warningThreshold);
        long overdueCount = partRepository.countByStatusAndAnalystAndStatusChangedAtLessThanEqual(PART_STATUS_IN_DETAILED_ANALYSIS, analyst, overdueThreshold);

        long approvalCount = isApprovalRole(roleNames)
            ? analysisReportRepository.countByStatus("submitted")
            : 0;

        long scrapConfirmCount = analysisOrderRepository.countByStatusAndAnalyst(ANALYSIS_ORDER_WORKON_SCRAP_IN_PROGRESS, analyst);

        List<TaskDTO> tasks = new ArrayList<>();
        tasks.add(task("initial_analysis", "待初分析", initialAnalysisCount, "medium", 1));
        tasks.add(task("detailed_analysis", "待精分析", detailedAnalysisCount, "medium", 2));
        tasks.add(task("warning", "精分析预警", warningCount, "high", 3));
        tasks.add(task("overdue", "精分析超期", overdueCount, "urgent", 4));
        tasks.add(task("approval", "精分析报告待审批", approvalCount, "medium", 5));
        tasks.add(task("scrap_confirm", "报废审批确认", scrapConfirmCount, "medium", 6));
        return tasks;
    }

    private TaskDTO task(String type, String title, long count, String priority, int id) {
        return TaskDTO.builder()
            .id(String.valueOf(id))
            .type(type)
            .title(title)
            .count((int) count)
            .priority(priority)
            .build();
    }

    public List<TrendDataPointDTO> getTrend(int days) {
        int safeDays = days <= 0 ? 30 : days;
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(safeDays - 1L);

        Map<LocalDate, Long> orderCountMap = new HashMap<>();
        for (Object[] row : returnOrderRepository.countDailyByReceiveDateBetween(start, end)) {
            LocalDate day = toLocalDate(row[0]);
            if (day != null) {
                orderCountMap.put(day, ((Number) row[1]).longValue());
            }
        }

        Map<LocalDate, Long> partCountMap = new HashMap<>();
        for (Object[] row : partRepository.countDailyByCreatedDateBetween(start, end)) {
            LocalDate day = toLocalDate(row[0]);
            if (day != null) {
                partCountMap.put(day, ((Number) row[1]).longValue());
            }
        }

        List<TrendDataPointDTO> trend = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            LocalDate date = start.plusDays(i);
            trend.add(TrendDataPointDTO.builder()
                .date(date.toString())
                .orders(orderCountMap.getOrDefault(date, 0L).intValue())
                .parts(partCountMap.getOrDefault(date, 0L).intValue())
                .build());
        }
        return trend;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        String text = value.toString();
        if (text.length() >= 10) {
            return LocalDate.parse(text.substring(0, 10));
        }
        return LocalDate.parse(text);
    }

    public List<ChartDataItemDTO> getCustomerRanking() {
        Map<String, Long> countMap = returnOrderRepository.findAll().stream()
            .collect(Collectors.groupingBy(
                order -> {
                    if (order.getCustomer() != null && !order.getCustomer().isBlank()) {
                        return order.getCustomer();
                    }
                    if (order.getCustomerId() != null && !order.getCustomerId().isBlank()) {
                        return order.getCustomerId();
                    }
                    return "UNKNOWN";
                },
                Collectors.counting()
            ));

        return countMap.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(10)
            .map(e -> ChartDataItemDTO.builder().name(e.getKey()).value(e.getValue()).build())
            .toList();
    }

    public List<ChartDataItemDTO> getFailureModeDistribution() {
        Map<String, Long> countMap = partRepository.findAll().stream()
            .map(Part::getFailureType)
            .filter(v -> v != null && !v.isBlank())
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        return countMap.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .map(e -> ChartDataItemDTO.builder().name(e.getKey()).value(e.getValue()).build())
            .toList();
    }

    public List<ChartDataItemDTO> getBuDistribution() {
        Map<String, Long> countMap = partRepository.findAll().stream()
            .map(Part::getBusinessUnit)
            .filter(v -> v != null && !v.isBlank())
            .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        return countMap.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .map(e -> ChartDataItemDTO.builder().name(e.getKey()).value(e.getValue()).build())
            .toList();
    }

    public List<ProcessingTimeDTO> getProcessingTimeData() {
        Map<String, List<Long>> stageDays = new HashMap<>();

        for (Part part : partRepository.findAll()) {
            if (part.getCreatedAt() == null || part.getStatusChangedAt() == null) {
                continue;
            }
            long days = Math.max(0, Duration.between(part.getCreatedAt(), part.getStatusChangedAt()).toDays());
            String stage = switch (part.getStatus()) {
                case PART_STATUS_IN_INITIAL_ANALYSIS -> "初分析";
                case PART_STATUS_IN_DETAILED_ANALYSIS -> "精分析";
                case "pending_approval" -> "审批";
                case "scrap_in_progress", PART_STATUS_SCRAPPED -> "报废";
                default -> "其他";
            };
            stageDays.computeIfAbsent(stage, k -> new ArrayList<>()).add(days);
        }

        return stageDays.entrySet().stream()
            .map(e -> {
                double avg = e.getValue().stream().mapToLong(Long::longValue).average().orElse(0.0);
                return ProcessingTimeDTO.builder()
                    .stage(e.getKey())
                    .avgDays(Math.round(avg * 100.0) / 100.0)
                    .build();
            })
            .sorted(Comparator.comparing(ProcessingTimeDTO::getStage))
            .toList();
    }

    private boolean isApprovalRole(String roleNames) {
        if (roleNames == null) {
            return false;
        }
        return roleNames.contains("W_RBCC_AEP_WFAM_QMC_Leader")
            || roleNames.contains("W_RBCC_AEP_WFAM_QMC_Manager");
    }
}
