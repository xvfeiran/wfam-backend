package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateFieldDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReportTemplate;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisReportRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReportTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ofPattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportTemplateService {

    private final ReportTemplateRepository repository;
    private final AnalysisReportRepository analysisReportRepository;
    private final ExcelTemplateParserService parserService;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;

    public List<ReportTemplateDTO> getAll() {
        return repository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public List<ReportTemplateDTO> getEnabled() {
        return repository.findByEnabled(1).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public ReportTemplateDTO getById(String id) {
        return repository.findById(id)
            .map(this::toDTO)
            .orElse(null);
    }

    @Transactional
    public ReportTemplateDTO uploadAndParse(MultipartFile file, String productPlatform, String failureType, String name) {
        try {
            log.info("Uploading template: platform={}, failureType={}, name={}, file={}", productPlatform, failureType, name, file.getOriginalFilename());

            List<ReportTemplateFieldDTO> fields = parserService.parseTemplate(file);
            String storedName = generateFileName(productPlatform, failureType, file.getOriginalFilename());
            String relativePath = fileStorageService.store("templates", storedName, file);

            // 将空字符串转换为null，便于匹配逻辑处理
            String normalizedFailureType = (failureType != null && !failureType.trim().isEmpty()) ? failureType : null;

            // 使用自定义名称或默认名称
            String templateName = (name != null && !name.trim().isEmpty()) ? name.trim() : storedName;

            ReportTemplate template = ReportTemplate.builder()
                .id(UUID.randomUUID().toString())
                .name(templateName)
                .productPlatform(productPlatform)
                .failureType(normalizedFailureType)
                .filePath(relativePath)
                .fileName(file.getOriginalFilename())
                .fieldDefinitions(objectMapper.writeValueAsString(fields))
                .enabled(1)
                .build();
            template = repository.save(template);
            log.info("Template uploaded successfully: id={}, name={}, platform={}, failureType={}",
                template.getId(), template.getName(), template.getProductPlatform(), template.getFailureType());
            return toDTO(template);
        } catch (IOException e) {
            log.error("Failed to upload template", e);
            throw new RuntimeException("Failed to upload template", e);
        }
    }

    public ReportTemplateDTO matchTemplate(String productPlatform, String failureType) {
        List<ReportTemplate> templates = repository
            .findByProductPlatformAndFailureTypeAndEnabled(productPlatform, failureType, 1);
        if (!templates.isEmpty()) {
            log.debug("Exact match found for platform={} and failureType={}", productPlatform, failureType);
            return toDTO(templates.get(0));
        }

        templates = repository.findByProductPlatformAndEnabled(productPlatform, 1);
        if (!templates.isEmpty()) {
            log.debug("Platform match found for platform={}", productPlatform);
            return toDTO(templates.get(0));
        }

        templates = repository.findByEnabled(1);
        return templates.isEmpty() ? null : toDTO(templates.get(0));
    }

    public List<ReportTemplateDTO> matchAllTemplates(String productPlatform, String failureType) {
        log.debug("Finding all matching templates for platform={} and failureType={}", productPlatform, failureType);

        List<ReportTemplate> templates = repository
            .findByProductPlatformAndFailureTypeAndEnabled(productPlatform, failureType, 1);
        if (!templates.isEmpty()) {
            log.debug("Found {} exact match templates for platform={} and failureType={}", templates.size(), productPlatform, failureType);
            return templates.stream().map(this::toDTO).collect(Collectors.toList());
        }

        templates = repository.findByProductPlatformAndEnabled(productPlatform, 1);
        if (!templates.isEmpty()) {
            log.debug("Found {} platform match templates for platform={}", templates.size(), productPlatform);
            return templates.stream().map(this::toDTO).collect(Collectors.toList());
        }

        templates = repository.findByEnabled(1);
        log.debug("Found {} default templates", templates.size());
        return templates.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public String getTemplateFilePath(String id) {
        ReportTemplate template = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        return template.getFilePath();
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Template not found: " + id);
        }

        long usageCount = analysisReportRepository.countByTemplateId(id);
        if (usageCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该模板已被使用，不能删除");
        }

        repository.deleteById(id);
        log.info("Template deleted: id={}", id);
    }

    private String generateFileName(String productPlatform, String failureType, String originalFileName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String failureTypeStr = (failureType != null && !failureType.trim().isEmpty()) ? failureType : "通用";
        return String.format("%s-%s-%s%s", productPlatform, failureTypeStr, timestamp, extension);
    }

    private ReportTemplateDTO toDTO(ReportTemplate entity) {
        List<ReportTemplateFieldDTO> fields = parseFieldDefinitions(entity.getFieldDefinitions());
        DateTimeFormatter formatter = ofPattern("yyyy-MM-dd HH:mm");
        return ReportTemplateDTO.builder()
            .id(entity.getId())
            .name(entity.getName())
            .productPlatform(entity.getProductPlatform())
            .failureType(entity.getFailureType())
            .fields(fields)
            .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(formatter) : null)
            .createdBy(entity.getCreatedBy())
            .build();
    }

    private List<ReportTemplateFieldDTO> parseFieldDefinitions(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse field definitions", e);
            return List.of();
        }
    }
}
