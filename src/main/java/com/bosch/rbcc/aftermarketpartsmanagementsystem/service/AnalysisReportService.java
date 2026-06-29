package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisReportDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImageUploadResult;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisReportService {

    private static final String STATUS_ANALYSIS_REPORT_SUBMITTED = "analysis_report_submitted";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";
    private static final String STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String STATUS_ANALYSIS_COMPLETED = "analysis_completed";

    private final AnalysisReportRepository repository;
    private final ObjectMapper objectMapper;
    private final PartRepository partRepository;
    private final AnalysisOrderRepository analysisOrderRepository;
    private final FileStorageService fileStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;

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
    public AnalysisReportDTO createOrUpdate(AnalysisReportDTO dto, String username) {
        Optional<AnalysisReport> existing = repository.findByPartId(dto.getPartId());
        AnalysisReport report;

        if (existing.isPresent()) {
            report = existing.get();
            if ("approved".equals(report.getStatus())) {
                throw new IllegalStateException("Cannot modify an approved analysis report");
            }
            updateReportFromDTO(report, dto, username);
        } else {
            report = createReportFromDTO(dto, username);
        }
        report = repository.save(report);
        // Write responsibility to dedicated column via JDBC
        if (dto.getResponsibility() != null) {
            jdbcTemplate.update(
                "UPDATE APMS_ANALYSIS_REPORT SET RESPONSIBILITY = ? WHERE ID = ?",
                dto.getResponsibility(), report.getId());
            // 同步回写到零件的“博世失效类型”字段，供前端基本信息直接展示（避免前端耦合精分析报告）
            jdbcTemplate.update(
                "UPDATE APMS_PART SET BOSCH_FAILURE_TYPE = ? WHERE ID = ?",
                dto.getResponsibility(), report.getPartId());
        }
        log.info("Report saved: id={}, partId={}", report.getId(), report.getPartId());
        return toDTO(report);
    }

    @Transactional
    public AnalysisReportDTO submit(String reportId, String submittedBy) {
        AnalysisReport report = repository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        String previousStatus = report.getStatus();
        report.setStatus("submitted");
        report.setSubmittedBy(submittedBy);
        report.setSubmittedAt(LocalDateTime.now());
        report = repository.save(report);
        log.info("Report submitted: id={}, by={}", reportId, submittedBy);

        // 仅在首次提交（draft → submitted）时触发责任判定通知，避免重复提交导致重复发送。
        // 责任值通过 JDBC 读取（entity 未映射 RESPONSIBILITY 列）；
        // sendResponsibilityNotification 内部会过滤掉非 B/O 值（含 null）。
        if (!"submitted".equals(previousStatus)) {
            try {
                String responsibility = jdbcTemplate.queryForObject(
                    "SELECT RESPONSIBILITY FROM APMS_ANALYSIS_REPORT WHERE ID = ?",
                    String.class, report.getId());
                if (responsibility != null) {
                    notificationService.sendResponsibilityNotification(report.getPartId(), responsibility);
                }
            } catch (Exception e) {
                log.warn("Responsibility notification failed on submit: {}", e.getMessage());
            }
        }

        // 联动：Part → analysis_report_submitted
        partRepository.findById(report.getPartId()).ifPresent(part -> {
            part.setStatus(STATUS_ANALYSIS_REPORT_SUBMITTED);
            part.setStatusChangedAt(LocalDateTime.now());
            partRepository.save(part);

            // 联动：若所有抽样件均为 analysis_report_submitted → AnalysisOrder → pending_approval
            analysisOrderRepository.findByOrderIdAndAnalyst(part.getOrderId(), part.getAnalyst())
                .ifPresent(ao -> {
                    List<Part> sampledParts = partRepository
                        .findByOrderIdAndAnalyst(part.getOrderId(), part.getAnalyst())
                        .stream().filter(p -> p.getIsSample() != null && p.getIsSample() == 1)
                        .toList();
                    boolean allReportSubmitted = !sampledParts.isEmpty()
                        && sampledParts.stream().allMatch(p -> STATUS_ANALYSIS_REPORT_SUBMITTED.equals(p.getStatus()));
                    if (allReportSubmitted) {
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

        // 联动：Part → analysis_completed
        partRepository.findById(report.getPartId()).ifPresent(part -> {
            part.setStatus(STATUS_ANALYSIS_COMPLETED);
            part.setStatusChangedAt(LocalDateTime.now());
            partRepository.save(part);

            // 联动：若所有抽样件均为 analysis_completed → AnalysisOrder → analysis_completed
            analysisOrderRepository.findByOrderIdAndAnalyst(part.getOrderId(), part.getAnalyst())
                .ifPresent(ao -> {
                    List<Part> sampledParts = partRepository
                        .findByOrderIdAndAnalyst(part.getOrderId(), part.getAnalyst())
                        .stream().filter(p -> p.getIsSample() != null && p.getIsSample() == 1)
                        .toList();
                    boolean allCompleted = !sampledParts.isEmpty()
                        && sampledParts.stream().allMatch(p -> STATUS_ANALYSIS_COMPLETED.equals(p.getStatus()));
                    if (allCompleted) {
                        ao.setStatus(STATUS_ANALYSIS_COMPLETED);
                        ao.setStatusChangedAt(LocalDateTime.now());
                        analysisOrderRepository.save(ao);
                    }
                });
        });

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

        // 联动：Part → in_detailed_analysis
        partRepository.findById(report.getPartId()).ifPresent(part -> {
            part.setStatus(STATUS_IN_DETAILED_ANALYSIS);
            part.setStatusChangedAt(LocalDateTime.now());
            partRepository.save(part);

            // 联动：若 AnalysisOrder 当前为 pending_approval → 回退为 in_detailed_analysis
            analysisOrderRepository.findByOrderIdAndAnalyst(part.getOrderId(), part.getAnalyst())
                .ifPresent(ao -> {
                    if (STATUS_PENDING_APPROVAL.equals(ao.getStatus())) {
                        ao.setStatus(STATUS_IN_DETAILED_ANALYSIS);
                        ao.setStatusChangedAt(LocalDateTime.now());
                        analysisOrderRepository.save(ao);
                    }
                });
        });

        return toDTO(report);
    }

    @Transactional
    public AnalysisReportDTO withdraw(String reportId, String username) {
        AnalysisReport report = repository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        // Validate: only submitter can withdraw (skip when username is null, e.g. unauthenticated dev env)
        if (username != null && !username.equals(report.getSubmittedBy())) {
            throw new IllegalStateException("Only the submitter can withdraw the report");
        }

        // Status change: submitted -> withdrawn (keep submittedBy/submittedAt for history)
        report.setStatus("withdrawn");
        report = repository.save(report);
        log.info("Report withdrawn: id={}, by={}", reportId, username);

        // 联动：Part → in_detailed_analysis
        partRepository.findById(report.getPartId()).ifPresent(part -> {
            part.setStatus(STATUS_IN_DETAILED_ANALYSIS);
            part.setStatusChangedAt(LocalDateTime.now());
            partRepository.save(part);

            // 联动：若 AnalysisOrder 当前为 pending_approval → 回退为 in_detailed_analysis
            analysisOrderRepository.findByOrderIdAndAnalyst(part.getOrderId(), part.getAnalyst())
                .ifPresent(ao -> {
                    if (STATUS_PENDING_APPROVAL.equals(ao.getStatus())) {
                        ao.setStatus(STATUS_IN_DETAILED_ANALYSIS);
                        ao.setStatusChangedAt(LocalDateTime.now());
                        analysisOrderRepository.save(ao);
                    }
                });
        });

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

    private AnalysisReport createReportFromDTO(AnalysisReportDTO dto, String username) {
        String status = dto.getStatus() != null ? dto.getStatus() : "draft";
        AnalysisReport.AnalysisReportBuilder builder = AnalysisReport.builder()
            .id(UUID.randomUUID().toString())
            .partId(dto.getPartId())
            .templateId(dto.getTemplateId())
            .content(serializeContent(dto.getContent()))
            .summary(dto.getSummary())
            .status(status)
            .attachments(serializeAttachments(dto.getAttachments()));
        if ("submitted".equals(status)) {
            builder.submittedBy(username).submittedAt(LocalDateTime.now());
        }
        // Inject responsibility into content JSON for Excel export
        if (dto.getResponsibility() != null) {
            Map<String, Object> contentMap = dto.getContent() != null ? new HashMap<>(dto.getContent()) : new HashMap<>();
            contentMap.put("responsibility", dto.getResponsibility());
            builder.content(serializeContent(contentMap));
        }
        return builder.build();
    }

    private void updateReportFromDTO(AnalysisReport report, AnalysisReportDTO dto, String username) {
        report.setTemplateId(dto.getTemplateId());
        report.setContent(serializeContent(dto.getContent()));
        report.setSummary(dto.getSummary());
        if (dto.getStatus() != null) {
            report.setStatus(dto.getStatus());
        }
        report.setAttachments(serializeAttachments(dto.getAttachments()));
        if ("submitted".equals(dto.getStatus())) {
            report.setSubmittedBy(username);
            report.setSubmittedAt(LocalDateTime.now());
        }
        // Inject responsibility into content JSON
        if (dto.getResponsibility() != null) {
            Map<String, Object> contentMap = dto.getContent() != null ? new HashMap<>(dto.getContent()) : new HashMap<>();
            contentMap.put("responsibility", dto.getResponsibility());
            report.setContent(serializeContent(contentMap));
        }
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
        Map<String, Object> content = parseContent(entity.getContent());
        String responsibility = getResponsibilityFromDb(entity.getId());
        if (responsibility == null && content != null && content.containsKey("responsibility")) {
            responsibility = String.valueOf(content.get("responsibility"));
        }
        return AnalysisReportDTO.builder()
            .id(entity.getId())
            .partId(entity.getPartId())
            .templateId(entity.getTemplateId())
            .content(content)
            .summary(entity.getSummary())
            .status(entity.getStatus())
            .responsibility(responsibility)
            .attachments(parseAttachments(entity.getAttachments()))
            .submittedBy(entity.getSubmittedBy())
            .submittedAt(formatDateTime(entity.getSubmittedAt()))
            .approvedBy(entity.getApprovedBy())
            .approvedAt(formatDateTime(entity.getApprovedAt()))
            .rejectReason(entity.getRejectReason())
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

    private static final String ANALYSIS_CATEGORY = "analysis";

    @Transactional
    public ImageUploadResult uploadAttachment(String reportId, MultipartFile file) {
        AnalysisReport report = repository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "精分析报告不存在: " + reportId));

        String ext = getExtension(file.getOriginalFilename());
        String relativePath = fileStorageService.store(ANALYSIS_CATEGORY, reportId + "/" + UUID.randomUUID() + ext, file);

        List<String> attachments = new ArrayList<>(parseAttachments(report.getAttachments()));
        attachments.add(relativePath);
        report.setAttachments(serializeAttachments(attachments));
        repository.save(report);

        return ImageUploadResult.builder()
                .relativePath(relativePath)
                .url("/api/v1/files/" + relativePath)
                .build();
    }

    @Transactional
    public void deleteAttachment(String reportId, String attachmentRelativePath) {
        AnalysisReport report = repository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "精分析报告不存在: " + reportId));

        List<String> attachments = new ArrayList<>(parseAttachments(report.getAttachments()));
        if (!attachments.contains(attachmentRelativePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在: " + attachmentRelativePath);
        }

        attachments.remove(attachmentRelativePath);
        report.setAttachments(attachments.isEmpty() ? null : serializeAttachments(attachments));
        repository.save(report);

        fileStorageService.delete(ANALYSIS_CATEGORY, attachmentRelativePath.substring(ANALYSIS_CATEGORY.length() + 1));
    }

    private String getExtension(String filename) {
        if (filename == null) return ".bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".bin";
    }

    private String getResponsibilityFromDb(String reportId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT RESPONSIBILITY FROM APMS_ANALYSIS_REPORT WHERE ID = ?",
                String.class, reportId);
        } catch (Exception e) {
            return null;
        }
    }
}
