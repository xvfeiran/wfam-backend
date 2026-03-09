package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.ReportTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/report-templates")
@RequiredArgsConstructor
@Tag(name = "Report Template Management", description = "APIs for managing report templates")
public class ReportTemplateController {

    private final ReportTemplateService templateService;

    @GetMapping
    @Operation(summary = "Get all templates", description = "Retrieve all report templates")
    public List<ReportTemplateDTO> getAll() {
        return templateService.getAll();
    }

    @GetMapping("/enabled")
    @Operation(summary = "Get enabled templates", description = "Retrieve all enabled report templates")
    public List<ReportTemplateDTO> getEnabled() {
        return templateService.getEnabled();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get template by ID", description = "Retrieve a specific template by its ID")
    public ReportTemplateDTO getById(@PathVariable String id) {
        return templateService.getById(id);
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload and parse template", description = "Upload an Excel template file and parse its field definitions")
    public ReportTemplateDTO uploadTemplate(
        @RequestParam("file") MultipartFile file,
        @RequestParam("productPlatform") String productPlatform,
        @RequestParam(value = "failureType", required = false) String failureType,
        @RequestParam(value = "name", required = false) String name
    ) {
        return templateService.uploadAndParse(file, productPlatform, failureType, name);
    }

    @GetMapping("/match")
    @Operation(summary = "Match template", description = "Find the best matching template based on product platform and failure type")
    public ReportTemplateDTO matchTemplate(
        @RequestParam String productPlatform,
        @RequestParam(required = false) String failureType
    ) {
        return templateService.matchTemplate(productPlatform, failureType);
    }

    @GetMapping("/match-all")
    @Operation(summary = "Match all templates", description = "Find all matching templates based on product platform and failure type")
    public List<ReportTemplateDTO> matchAllTemplates(
        @RequestParam String productPlatform,
        @RequestParam(required = false) String failureType
    ) {
        return templateService.matchAllTemplates(productPlatform, failureType);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download template file", description = "Download the Excel template file")
    public ResponseEntity<Resource> downloadTemplate(@PathVariable String id) {
        Resource resource = templateService.downloadTemplate(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
            .body(resource);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete template", description = "Delete a template by its ID")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
