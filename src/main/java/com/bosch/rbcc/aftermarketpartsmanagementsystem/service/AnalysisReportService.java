package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisReportDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisReport;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisReportRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisReportService {

    private static final String STATUS_PENDING_APPROVAL = "pending_approval";
    private static final String STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String STATUS_ANALYSIS_COMPLETED = "analysis_completed";

    private final AnalysisReportRepository repository;
    private final ObjectMapper objectMapper;
    private final PartRepository partRepository;
    private final AnalysisOrderRepository analysisOrderRepository;

    public List<AnalysisReportDTO> getAll() {
        return repository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public List<AnalysisReportDTO> getByPartId(String partId) {
        return repository.findAll().stream()
            .filter(r -> r.getPartId().equals(partId))
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public AnalysisReportDTO getById(String id) {
        return repository.findById(id)
            .map(this::toDTO)
            .orElse(null);
    }

    public AnalysisReportDTO getByPartIdLatest(String partId) {
        return repository.findByPartId(partId)
            .map(this::toDTO)
            .orElse(null);
    }

    @Transactional
    public AnalysisReportDTO createOrUpdate(AnalysisReportDTO dto) {
        Optional<AnalysisReport> existing = repository.findByPartId(dto.getPartId());
        AnalysisReport report;

        if (existing.isPresent()) {
            report = existing.get();
            updateReportFromDTO(report, dto);
        } else {
            report = createReportFromDTO(dto);
        }
        report = repository.save(report);
        log.info("Report saved: id={}, partId={}", report.getId(), report.getPartId());
        return toDTO(report);
    }

    @Transactional
    public AnalysisReportDTO submit(String reportId, String submittedBy) {
        AnalysisReport report = repository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        report.setStatus("submitted");
        report.setSubmittedBy(submittedBy);
        report.setSubmittedAt(LocalDateTime.now());
        report = repository.save(report);
        log.info("Report submitted: id={}, by={}", reportId, submittedBy);

        // 联动：Part → pending_approval
        partRepository.findById(report.getPartId()).ifPresent(part -> {
            part.setStatus(STATUS_PENDING_APPROVAL);
            part.setStatusChangedAt(LocalDateTime.now());
            partRepository.save(part);

            // 联动：若所有抽样件均为 pending_approval → AnalysisOrder → pending_approval
            analysisOrderRepository.findByOrderIdAndAnalyst(part.getOrderId(), part.getAnalyst())
                .ifPresent(ao -> {
                    List<Part> sampledParts = partRepository
                        .findByOrderIdAndAnalyst(part.getOrderId(), part.getAnalyst())
                        .stream().filter(p -> p.getIsSample() != null && p.getIsSample() == 1)
                        .toList();
                    boolean allPendingApproval = !sampledParts.isEmpty()
                        && sampledParts.stream().allMatch(p -> STATUS_PENDING_APPROVAL.equals(p.getStatus()));
                    if (allPendingApproval) {
                        ao.setStatus(STATUS_PENDING_APPROVAL);
                        ao.setStatusChangedAt(LocalDateTime.now());
                        analysisOrderRepository.save(ao);
                    }
                });
        });

        return toDTO(report);
    }

    @Transactional
    public AnalysisReportDTO approve(String reportId, String approvedBy, String comment) {
        AnalysisReport report = repository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        report.setStatus("approved");
        report.setApprovedBy(approvedBy);
        report.setApprovedAt(LocalDateTime.now());
        report = repository.save(report);
        log.info("Report approved: id={}, by={}", reportId, approvedBy);
        return toDTO(report);
    }

    @Transactional
    public AnalysisReportDTO reject(String reportId, String approvedBy, String reason) {
        AnalysisReport report = repository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        report.setStatus("rejected");
        report.setApprovedBy(approvedBy);
        report.setApprovedAt(LocalDateTime.now());
        report.setRejectReason(reason);
        report = repository.save(report);
        log.info("Report rejected: id={}, by={}, reason={}", reportId, approvedBy, reason);
        return toDTO(report);
    }

    @Transactional
    public AnalysisReportDTO withdraw(String reportId, String username) {
        AnalysisReport report = repository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        // Validate: only submitter can withdraw
        if (!username.equals(report.getSubmittedBy())) {
            throw new IllegalStateException("Only the submitter can withdraw the report");
        }

        // Status change: submitted -> draft
        report.setStatus("draft");
        report.setSubmittedBy(null);
        report.setSubmittedAt(null);
        report = repository.save(report);
        log.info("Report withdrawn: id={}, by={}", reportId, username);
        return toDTO(report);
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Report not found: " + id);
        }
        repository.deleteById(id);
        log.info("Report deleted: id={}", id);
    }

    private AnalysisReport createReportFromDTO(AnalysisReportDTO dto) {
        return AnalysisReport.builder()
            .id(UUID.randomUUID().toString())
            .partId(dto.getPartId())
            .templateId(dto.getTemplateId())
            .content(serializeContent(dto.getContent()))
            .summary(dto.getSummary())
            .status(dto.getStatus() != null ? dto.getStatus() : "draft")
            .attachments(serializeAttachments(dto.getAttachments()))
            .build();
    }

    private void updateReportFromDTO(AnalysisReport report, AnalysisReportDTO dto) {
        report.setTemplateId(dto.getTemplateId());
        report.setContent(serializeContent(dto.getContent()));
        report.setSummary(dto.getSummary());
        if (dto.getStatus() != null) {
            report.setStatus(dto.getStatus());
        }
        report.setAttachments(serializeAttachments(dto.getAttachments()));
    }

    private String serializeContent(Map<String, Object> content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize content", e);
            return null;
        }
    }

    private String serializeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize attachments", e);
            return null;
        }
    }

    private AnalysisReportDTO toDTO(AnalysisReport entity) {
        return AnalysisReportDTO.builder()
            .id(entity.getId())
            .partId(entity.getPartId())
            .templateId(entity.getTemplateId())
            .content(parseContent(entity.getContent()))
            .summary(entity.getSummary())
            .status(entity.getStatus())
            .attachments(parseAttachments(entity.getAttachments()))
            .submittedBy(entity.getSubmittedBy())
            .submittedAt(formatDateTime(entity.getSubmittedAt()))
            .approvedBy(entity.getApprovedBy())
            .approvedAt(formatDateTime(entity.getApprovedAt()))
            .createdBy(entity.getCreatedBy())
            .createdAt(formatDateTime(entity.getCreatedAt()))
            .build();
    }

    private Map<String, Object> parseContent(String json) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse content", e);
            return Map.of();
        }
    }

    private List<String> parseAttachments(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse attachments", e);
            return List.of();
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }
}
