package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisApplicationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisReport;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisReportRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalService {

    private final AnalysisReportRepository analysisReportRepository;
    private final PartRepository partRepository;
    private final ObjectMapper objectMapper;

    /**
     * Get analysis applications submitted by current user
     */
    public List<AnalysisApplicationDTO> getMyAnalysisApplications(String username) {
        return analysisReportRepository.findBySubmittedBy(username).stream()
            .map(this::toApplicationDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get pending analysis approvals (status = 'submitted')
     */
    public List<AnalysisApplicationDTO> getPendingAnalysisApprovals() {
        return analysisReportRepository.findByStatus("submitted").stream()
            .map(this::toApplicationDTO)
            .collect(Collectors.toList());
    }

    /**
     * Convert AnalysisReport entity to AnalysisApplicationDTO
     */
    private AnalysisApplicationDTO toApplicationDTO(AnalysisReport report) {
        // Fetch related Part to get additional fields
        Part part = partRepository.findById(report.getPartId()).orElse(null);

        String reportNumber = "RPT-" + report.getId().substring(0, 8);
        String partNumber = part != null ? part.getPartNumber() : "";
        String productPlatform = part != null ? part.getProductPlatform() : "";
        String failureType = part != null ? part.getFailureType() : "";

        return AnalysisApplicationDTO.builder()
            .id(report.getId())
            .reportNumber(reportNumber)
            .partNumber(partNumber)
            .productPlatform(productPlatform)
            .failureType(failureType)
            .submitter(report.getSubmittedBy())
            .approver(report.getApprovedBy())
            .submitTime(formatDateTime(report.getSubmittedAt()))
            .approveTime(formatDateTime(report.getApprovedAt()))
            .status(mapStatus(report.getStatus()))
            .summary(report.getSummary())
            .content(parseContent(report.getContent()))
            .build();
    }

    private String mapStatus(String status) {
        if (status == null) {
            return "pending";
        }
        return switch (status) {
            case "submitted" -> "pending";
            case "approved" -> "approved";
            case "rejected" -> "rejected";
            case "withdrawn" -> "withdrawn";
            default -> "draft";
        };
    }

    private Map<String, String> parseContent(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse content", e);
            return new HashMap<>();
        }
    }

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String formatDateTime(java.time.LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DISPLAY_FORMATTER) : null;
    }
}
