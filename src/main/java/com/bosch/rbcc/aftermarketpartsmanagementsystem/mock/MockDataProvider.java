package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.*;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Central mock data provider that delegates to specialized generators.
 * This class serves as the main entry point for all mock data needs.
 */
@Component
@RequiredArgsConstructor
public class MockDataProvider {

    private final LookupDataGenerator lookupDataGenerator;
    private final OrderDataGenerator orderDataGenerator;
    private final PartDataGenerator partDataGenerator;
    private final TaskDataGenerator taskDataGenerator;
    private final TemplateDataGenerator templateDataGenerator;
    private final ChartDataGenerator chartDataGenerator;
    private final ApprovalDataGenerator approvalDataGenerator;
    private final ReportDataGenerator reportDataGenerator;

    // ========== Lookup Data ==========

    public List<String> getCustomers() {
        return lookupDataGenerator.getCustomers();
    }

    public List<String> getBusinessUnits() {
        return lookupDataGenerator.getBusinessUnits();
    }

    public List<String> getProductPlatforms() {
        return lookupDataGenerator.getProductPlatforms();
    }

    public List<String> getProductCategories() {
        return lookupDataGenerator.getProductCategories();
    }

    public List<String> getFailureTypes() {
        return lookupDataGenerator.getFailureTypes();
    }

    public List<java.util.Map<String, String>> getUsers() {
        return lookupDataGenerator.getUsers();
    }

    public List<java.util.Map<String, String>> getAnalysts() {
        return lookupDataGenerator.getAnalysts();
    }

    // ========== Return Orders ==========

    public List<ReturnOrderDTO> getOrders() {
        return orderDataGenerator.getOrders();
    }

    // ========== Parts ==========

    public List<PartDTO> getParts() {
        return partDataGenerator.getParts();
    }

    // ========== Tasks ==========

    public List<TaskDTO> getTasks() {
        return taskDataGenerator.getTasks();
    }

    // ========== Report Templates ==========

    public List<ReportTemplateDTO> getTemplates() {
        return templateDataGenerator.getTemplates();
    }

    // ========== Chart Data ==========

    public List<TrendDataPointDTO> generateTrendData(int days) {
        return chartDataGenerator.generateTrendData(days);
    }

    public List<ChartDataItemDTO> getCustomerRanking() {
        return chartDataGenerator.getCustomerRanking();
    }

    public List<ChartDataItemDTO> getFailureModeData() {
        return chartDataGenerator.getFailureModeData();
    }

    public List<ChartDataItemDTO> getBuDistribution() {
        return chartDataGenerator.getBuDistribution();
    }

    public List<ProcessingTimeDTO> getProcessingTimeData() {
        return chartDataGenerator.getProcessingTimeData();
    }

    // ========== Approval Data ==========

    public List<AnalysisApplicationDTO> getMyAnalysisApplications() {
        return approvalDataGenerator.getMyAnalysisApplications();
    }

    public List<AnalysisApplicationDTO> getPendingAnalysisApprovals() {
        return approvalDataGenerator.getPendingAnalysisApprovals();
    }

    // ========== Dashboard Stats ==========

    public DashboardStatsDTO getDashboardStats() {
        List<ReturnOrderDTO> orders = getOrders();
        List<PartDTO> parts = getParts();
        List<TaskDTO> tasks = getTasks();
        int pendingCount = tasks.stream().mapToInt(TaskDTO::getCount).sum();

        return DashboardStatsDTO.builder()
                .totalOrders(orders.size())
                .totalParts(parts.size())
                .pendingTasks(pendingCount)
                .completionRate(85.5)
                .build();
    }

    // ========== Analysis Reports ==========

    public List<AnalysisReportDTO> getReports() {
        return reportDataGenerator.getReports();
    }
}
