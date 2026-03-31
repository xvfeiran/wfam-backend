package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisReport;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisReportRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisReportServiceTest {

    @Mock AnalysisReportRepository repository;
    @Mock PartRepository partRepository;
    @Mock AnalysisOrderRepository analysisOrderRepository;
    @Mock ObjectMapper objectMapper;

    @InjectMocks AnalysisReportService service;

    private AnalysisReport report;
    private Part sampledPart;
    private AnalysisOrder analysisOrder;

    @BeforeEach
    void setUp() {
        report = AnalysisReport.builder()
                .id("r-1").partId("p-1").status("draft").build();

        sampledPart = Part.builder()
                .id("p-1").orderId("order-1").analyst("analyst1")
                .isSample(1).status("in_detailed_analysis").build();

        analysisOrder = AnalysisOrder.builder()
                .id("ao-1").orderId("order-1").analyst("analyst1")
                .status("in_detailed_analysis").build();
    }

    @Test
    void submit_shouldSetPartToPendingApproval() {
        when(repository.findById("r-1")).thenReturn(Optional.of(report));
        when(repository.save(any())).thenReturn(report);
        when(partRepository.findById("p-1")).thenReturn(Optional.of(sampledPart));
        when(analysisOrderRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(analysisOrder));
        // 只有一个抽样件，提交后已为 pending_approval，所以 findByOrderIdAndAnalyst 返回它
        when(partRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(List.of(sampledPart));

        service.submit("r-1", "analyst1");

        assertThat(sampledPart.getStatus()).isEqualTo("pending_approval");
    }

    @Test
    void submit_allSampledPartsPendingApproval_shouldSetAnalysisOrderToPendingApproval() {
        when(repository.findById("r-1")).thenReturn(Optional.of(report));
        when(repository.save(any())).thenReturn(report);
        when(partRepository.findById("p-1")).thenReturn(Optional.of(sampledPart));
        when(analysisOrderRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(analysisOrder));
        // 实现中先 setStatus("pending_approval") 再查，sampledPart 对象已被修改，allMatch 为 true
        when(partRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(List.of(sampledPart));

        service.submit("r-1", "analyst1");

        assertThat(analysisOrder.getStatus()).isEqualTo("pending_approval");
        verify(analysisOrderRepository).save(analysisOrder);
    }

    @Test
    void submit_notAllSampledPartsSubmitted_shouldNotUpdateAnalysisOrder() {
        Part sampledPart2 = Part.builder()
                .id("p-2").orderId("order-1").analyst("analyst1")
                .isSample(1).status("in_detailed_analysis").build();

        when(repository.findById("r-1")).thenReturn(Optional.of(report));
        when(repository.save(any())).thenReturn(report);
        when(partRepository.findById("p-1")).thenReturn(Optional.of(sampledPart));
        when(analysisOrderRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(analysisOrder));
        // sampledPart 会被设为 pending_approval，sampledPart2 仍为 in_detailed_analysis
        when(partRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(List.of(sampledPart, sampledPart2));

        service.submit("r-1", "analyst1");

        assertThat(analysisOrder.getStatus()).isEqualTo("in_detailed_analysis"); // 不变
        verify(analysisOrderRepository, never()).save(any());
    }

    @Test
    void approve_shouldSetPartToAnalysisCompleted() {
        report.setStatus("submitted");
        sampledPart.setStatus("pending_approval");
        analysisOrder.setStatus("pending_approval");

        when(repository.findById("r-1")).thenReturn(Optional.of(report));
        when(repository.save(any())).thenReturn(report);
        when(partRepository.findById("p-1")).thenReturn(Optional.of(sampledPart));
        when(analysisOrderRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(analysisOrder));
        // 只有一个抽样件，approve 后为 analysis_completed → all match → AnalysisOrder 也更新
        when(partRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(List.of(sampledPart));

        service.approve("r-1", "qmc-leader", null);

        assertThat(sampledPart.getStatus()).isEqualTo("analysis_completed");
        assertThat(analysisOrder.getStatus()).isEqualTo("analysis_completed");
        verify(analysisOrderRepository).save(analysisOrder);
    }

    @Test
    void approve_notAllPartsApproved_shouldNotUpdateAnalysisOrder() {
        report.setStatus("submitted");
        sampledPart.setStatus("pending_approval");
        analysisOrder.setStatus("pending_approval");
        Part sampledPart2 = Part.builder()
                .id("p-2").orderId("order-1").analyst("analyst1")
                .isSample(1).status("pending_approval").build();

        when(repository.findById("r-1")).thenReturn(Optional.of(report));
        when(repository.save(any())).thenReturn(report);
        when(partRepository.findById("p-1")).thenReturn(Optional.of(sampledPart));
        when(analysisOrderRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(analysisOrder));
        // sampledPart 变为 analysis_completed，sampledPart2 仍为 pending_approval → not all match
        when(partRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(List.of(sampledPart, sampledPart2));

        service.approve("r-1", "qmc-leader", null);

        assertThat(sampledPart.getStatus()).isEqualTo("analysis_completed");
        assertThat(analysisOrder.getStatus()).isEqualTo("pending_approval"); // 不变
        verify(analysisOrderRepository, never()).save(any());
    }

    @Test
    void reject_shouldSetPartBackToInDetailedAnalysis() {
        report.setStatus("submitted");
        sampledPart.setStatus("pending_approval");
        analysisOrder.setStatus("pending_approval");

        when(repository.findById("r-1")).thenReturn(Optional.of(report));
        when(repository.save(any())).thenReturn(report);
        when(partRepository.findById("p-1")).thenReturn(Optional.of(sampledPart));
        when(analysisOrderRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(analysisOrder));

        service.reject("r-1", "qmc-leader", "需要补充数据");

        assertThat(sampledPart.getStatus()).isEqualTo("in_detailed_analysis");
        assertThat(analysisOrder.getStatus()).isEqualTo("in_detailed_analysis");
        verify(analysisOrderRepository).save(analysisOrder);
    }

    @Test
    void reject_analysisOrderNotPendingApproval_shouldNotUpdateAnalysisOrder() {
        report.setStatus("submitted");
        sampledPart.setStatus("pending_approval");
        analysisOrder.setStatus("in_detailed_analysis"); // 已经回退过，不是 pending_approval

        when(repository.findById("r-1")).thenReturn(Optional.of(report));
        when(repository.save(any())).thenReturn(report);
        when(partRepository.findById("p-1")).thenReturn(Optional.of(sampledPart));
        when(analysisOrderRepository.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(analysisOrder));

        service.reject("r-1", "qmc-leader", "需要补充数据");

        assertThat(sampledPart.getStatus()).isEqualTo("in_detailed_analysis");
        verify(analysisOrderRepository, never()).save(any());
    }
}
