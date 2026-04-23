package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisReportDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaders;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.AnalysisReportService;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.ExcelReportGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analysis-reports")
@RequiredArgsConstructor
@Tag(name = "Analysis Report Management", description = "APIs for managing analysis reports")
public class AnalysisReportController {

    private final AnalysisReportService reportService;
    private final ExcelReportGeneratorService generatorService;

    @GetMapping
    @Operation(summary = "Get all reports", description = "Retrieve all analysis reports")
    public List<AnalysisReportDTO> getAll() {
        return reportService.getAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get report by ID", description = "Retrieve a specific report by its ID")
    public AnalysisReportDTO getById(@PathVariable String id) {
        return reportService.getById(id);
    }

    @GetMapping("/part/{partId}")
    @Operation(summary = "Get reports by part ID", description = "Retrieve all reports for a specific part")
    public List<AnalysisReportDTO> getByPartId(@PathVariable String partId) {
        return reportService.getByPartId(partId);
    }

    @GetMapping("/part/{partId}/latest")
    @Operation(summary = "Get latest report by part ID", description = "Retrieve the latest report for a specific part")
    public AnalysisReportDTO getLatestByPartId(@PathVariable String partId) {
        return reportService.getByPartIdLatest(partId);
    }

    @PostMapping
    @Operation(summary = "Create or update report", description = "Create a new report or update an existing one")
    public AnalysisReportDTO saveReport(@RequestBody AnalysisReportDTO dto) {
        return reportService.createOrUpdate(dto, getCurrentUsername());
    }

    private String getCurrentUsername() {
        CommonHeaders headers = CommonHeaderManager.getCommonHeaders();
        return headers != null && headers.getUsername() != null ? headers.getUsername() : "anonymous";
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit report for approval", description = "Submit a draft report for approval")
    public AnalysisReportDTO submitReport(
        @PathVariable String id,
        @Parameter(description = "Username of the submitter") @RequestParam String submittedBy
    ) {
        return reportService.submit(id, submittedBy);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve report", description = "Approve a submitted report")
    public AnalysisReportDTO approveReport(
        @PathVariable String id,
        @Parameter(description = "Username of the approver") @RequestParam String approvedBy,
        @Parameter(description = "Approval comment") @RequestParam(required = false) String comment
    ) {
        return reportService.approve(id, approvedBy, comment);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject report", description = "Reject a submitted report")
    public AnalysisReportDTO rejectReport(
        @PathVariable String id,
        @Parameter(description = "Username of the approver") @RequestParam String approvedBy,
        @Parameter(description = "Rejection reason") @RequestParam String reason
    ) {
        return reportService.reject(id, approvedBy, reason);
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Export report as Excel", description = "Generate and download an Excel report from the template")
    public ResponseEntity<byte[]> exportReport(@PathVariable String id) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            generatorService.generateReport(id, outputStream);
            String filename = "report_" + id + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            return ResponseEntity.ok()
                .headers(headers)
                .body(outputStream.toByteArray());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete report", description = "Delete a report by its ID")
    public ResponseEntity<Void> deleteReport(@PathVariable String id) {
        reportService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
