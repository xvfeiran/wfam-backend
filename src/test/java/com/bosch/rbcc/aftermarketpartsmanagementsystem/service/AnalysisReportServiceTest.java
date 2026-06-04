package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisReport;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisReportRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisReportServiceTest {

    @Mock private AnalysisReportRepository reportRepo;
    @Mock private PartRepository partRepo;
    @Mock private AnalysisOrderRepository aoRepo;
    @Mock private ObjectMapper objectMapper;
    @Mock private FileStorageService fileStorageService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AnalysisReportService service;

    // --- submit() tests ---

    @Test
    void submit_setsPartStatusToAnalysisReportSubmitted() {
        AnalysisReport report = AnalysisReport.builder()
            .id("r1").partId("p1").templateId("t1").status("draft").build();
        when(reportRepo.findById("r1")).thenReturn(Optional.of(report));
        when(reportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Part part = Part.builder().id("p1").orderId("o1").analyst("a1").status("in_detailed_analysis").build();
        when(partRepo.findById("p1")).thenReturn(Optional.of(part));
        when(partRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(aoRepo.findByOrderIdAndAnalyst("o1", "a1")).thenReturn(Optional.empty());

        service.submit("r1", "user1");

        // Verify report status changed to submitted
        ArgumentCaptor<AnalysisReport> reportCaptor = ArgumentCaptor.forClass(AnalysisReport.class);
        verify(reportRepo).save(reportCaptor.capture());
        assertEquals("submitted", reportCaptor.getValue().getStatus());
        assertEquals("user1", reportCaptor.getValue().getSubmittedBy());
        assertNotNull(reportCaptor.getValue().getSubmittedAt());

        // Verify part status changed to analysis_report_submitted
        ArgumentCaptor<Part> partCaptor = ArgumentCaptor.forClass(Part.class);
        verify(partRepo).save(partCaptor.capture());
        assertEquals("analysis_report_submitted", partCaptor.getValue().getStatus());
    }

    @Test
    void submit_whenAllSampledPartsSubmitted_updatesOrderToPendingApproval() {
        AnalysisReport report = AnalysisReport.builder()
            .id("r1").partId("p1").templateId("t1").status("draft").build();
        when(reportRepo.findById("r1")).thenReturn(Optional.of(report));
        when(reportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Part part1 = Part.builder().id("p1").orderId("o1").analyst("a1")
            .isSample(1).status("in_detailed_analysis").build();
        Part part2 = Part.builder().id("p2").orderId("o1").analyst("a1")
            .isSample(1).status("analysis_report_submitted").build();
        when(partRepo.findById("p1")).thenReturn(Optional.of(part1));
        when(partRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        AnalysisOrder ao = AnalysisOrder.builder().id("ao1").orderId("o1").analyst("a1")
            .status("in_detailed_analysis").build();
        when(aoRepo.findByOrderIdAndAnalyst("o1", "a1")).thenReturn(Optional.of(ao));
        when(aoRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // part1 will be updated to analysis_report_submitted, so both p1+p2 are submitted
        when(partRepo.findByOrderIdAndAnalyst("o1", "a1"))
            .thenReturn(List.of(part1, part2));

        service.submit("r1", "user1");

        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        verify(aoRepo).save(aoCaptor.capture());
        assertEquals("pending_approval", aoCaptor.getValue().getStatus());
    }

    @Test
    void submit_whenNotAllPartsSubmitted_keepsOrderInDetailedAnalysis() {
        AnalysisReport report = AnalysisReport.builder()
            .id("r1").partId("p1").templateId("t1").status("draft").build();
        when(reportRepo.findById("r1")).thenReturn(Optional.of(report));
        when(reportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Part part1 = Part.builder().id("p1").orderId("o1").analyst("a1")
            .isSample(1).status("in_detailed_analysis").build();
        Part part2 = Part.builder().id("p2").orderId("o1").analyst("a1")
            .isSample(1).status("in_detailed_analysis").build();  // not yet submitted
        when(partRepo.findById("p1")).thenReturn(Optional.of(part1));
        when(partRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        AnalysisOrder ao = AnalysisOrder.builder().id("ao1").orderId("o1").analyst("a1")
            .status("in_detailed_analysis").build();
        when(aoRepo.findByOrderIdAndAnalyst("o1", "a1")).thenReturn(Optional.of(ao));
        when(partRepo.findByOrderIdAndAnalyst("o1", "a1"))
            .thenReturn(List.of(part1, part2));

        service.submit("r1", "user1");

        // Order should NOT be updated to pending_approval since part2 is not submitted
        verify(aoRepo, never()).save(any());
    }

    @Test
    void submit_reportNotFound_throwsException() {
        when(reportRepo.findById("nonexistent")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.submit("nonexistent", "user1"));
    }

    // --- withdraw() tests ---

    @Test
    void withdraw_setsPartStatusBackToInDetailedAnalysis() {
        AnalysisReport report = AnalysisReport.builder()
            .id("r1").partId("p1").status("submitted").submittedBy("user1").build();
        when(reportRepo.findById("r1")).thenReturn(Optional.of(report));
        when(reportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Part part = Part.builder().id("p1").orderId("o1").analyst("a1")
            .status("analysis_report_submitted").build();
        when(partRepo.findById("p1")).thenReturn(Optional.of(part));
        when(partRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(aoRepo.findByOrderIdAndAnalyst("o1", "a1")).thenReturn(Optional.empty());

        service.withdraw("r1", "user1");

        ArgumentCaptor<AnalysisReport> reportCaptor = ArgumentCaptor.forClass(AnalysisReport.class);
        verify(reportRepo).save(reportCaptor.capture());
        assertEquals("withdrawn", reportCaptor.getValue().getStatus());

        ArgumentCaptor<Part> partCaptor = ArgumentCaptor.forClass(Part.class);
        verify(partRepo).save(partCaptor.capture());
        assertEquals("in_detailed_analysis", partCaptor.getValue().getStatus());
    }

    @Test
    void withdraw_whenOrderPendingApproval_revertsOrderToDetailedAnalysis() {
        AnalysisReport report = AnalysisReport.builder()
            .id("r1").partId("p1").status("submitted").submittedBy("user1").build();
        when(reportRepo.findById("r1")).thenReturn(Optional.of(report));
        when(reportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Part part = Part.builder().id("p1").orderId("o1").analyst("a1")
            .status("analysis_report_submitted").build();
        when(partRepo.findById("p1")).thenReturn(Optional.of(part));
        when(partRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        AnalysisOrder ao = AnalysisOrder.builder().id("ao1").orderId("o1").analyst("a1")
            .status("pending_approval").build();
        when(aoRepo.findByOrderIdAndAnalyst("o1", "a1")).thenReturn(Optional.of(ao));
        when(aoRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.withdraw("r1", "user1");

        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        verify(aoRepo).save(aoCaptor.capture());
        assertEquals("in_detailed_analysis", aoCaptor.getValue().getStatus());
    }

    @Test
    void withdraw_whenOrderNotPendingApproval_doesNotRevertOrder() {
        AnalysisReport report = AnalysisReport.builder()
            .id("r1").partId("p1").status("submitted").submittedBy("user1").build();
        when(reportRepo.findById("r1")).thenReturn(Optional.of(report));
        when(reportRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Part part = Part.builder().id("p1").orderId("o1").analyst("a1")
            .status("analysis_report_submitted").build();
        when(partRepo.findById("p1")).thenReturn(Optional.of(part));
        when(partRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        AnalysisOrder ao = AnalysisOrder.builder().id("ao1").orderId("o1").analyst("a1")
            .status("in_detailed_analysis").build();  // not pending_approval
        when(aoRepo.findByOrderIdAndAnalyst("o1", "a1")).thenReturn(Optional.of(ao));

        service.withdraw("r1", "user1");

        verify(aoRepo, never()).save(any());
    }

    @Test
    void withdraw_onlySubmitterCanWithdraw() {
        AnalysisReport report = AnalysisReport.builder()
            .id("r1").partId("p1").status("submitted").submittedBy("user1").build();
        when(reportRepo.findById("r1")).thenReturn(Optional.of(report));

        assertThrows(IllegalStateException.class, () -> service.withdraw("r1", "otherUser"));
    }

    @Test
    void withdraw_reportNotFound_throwsException() {
        when(reportRepo.findById("nonexistent")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.withdraw("nonexistent", "user1"));
    }
}
