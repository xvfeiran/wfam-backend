package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.part;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisReportDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "附件管理", description = "附件 CRUD、分析报告及模板匹配")
@RestController
@RequestMapping("/api/v1/parts")
@RequiredArgsConstructor
public class PartController {

    private final MockDataProvider mockData;

    @Operation(summary = "查询附件列表", description = "支持按编号、零件码、事业部、产品平台、状态筛选")
    @GetMapping
    public List<PartDTO> list(
            @Parameter(description = "附件编号（模糊匹配）") @RequestParam(required = false) String partNumber,
            @Parameter(description = "零件码（模糊匹配）") @RequestParam(required = false) String partCode,
            @Parameter(description = "事业部") @RequestParam(required = false) String businessUnit,
            @Parameter(description = "产品平台") @RequestParam(required = false) String productPlatform,
            @Parameter(description = "附件状态") @RequestParam(required = false) String status) {
        List<PartDTO> parts = mockData.getParts();
        if (partNumber != null && !partNumber.isEmpty()) {
            parts = parts.stream()
                    .filter(p -> p.getPartNumber().toLowerCase().contains(partNumber.toLowerCase()))
                    .toList();
        }
        if (partCode != null && !partCode.isEmpty()) {
            parts = parts.stream()
                    .filter(p -> p.getPartCode().toLowerCase().contains(partCode.toLowerCase()))
                    .toList();
        }
        if (businessUnit != null && !businessUnit.isEmpty()) {
            parts = parts.stream()
                    .filter(p -> p.getBusinessUnit().equals(businessUnit))
                    .toList();
        }
        if (productPlatform != null && !productPlatform.isEmpty()) {
            parts = parts.stream()
                    .filter(p -> p.getProductPlatform().equals(productPlatform))
                    .toList();
        }
        if (status != null && !status.isEmpty()) {
            parts = parts.stream()
                    .filter(p -> p.getStatus().equals(status))
                    .toList();
        }
        return parts;
    }

    @Operation(summary = "获取附件详情")
    @GetMapping("/{id}")
    public PartDTO getById(@PathVariable String id) {
        return mockData.getParts().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));
    }

    @Operation(summary = "新建附件")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartDTO create(@RequestBody PartDTO dto) {
        return dto;
    }

    @Operation(summary = "更新附件")
    @PutMapping("/{id}")
    public PartDTO update(@PathVariable String id, @RequestBody PartDTO dto) {
        dto.setId(id);
        return dto;
    }

    @Operation(summary = "删除附件")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        // mock - no persistence
    }

    @Operation(summary = "获取附件的分析报告")
    @GetMapping("/{id}/reports")
    public List<AnalysisReportDTO> getReports(@PathVariable String id) {
        return mockData.getReports().stream()
                .filter(r -> r.getPartId().equals(id))
                .toList();
    }

    @Operation(summary = "获取附件匹配的分析模板", description = "根据产品平台和失效类型匹配模板，无匹配则返回默认模板")
    @GetMapping("/{id}/templates")
    public ReportTemplateDTO getMatchedTemplate(@PathVariable String id) {
        PartDTO part = mockData.getParts().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));

        return mockData.getTemplates().stream()
                .filter(t -> t.getProductPlatform().equals(part.getProductPlatform())
                        && t.getFailureType().equals(part.getFailureType()))
                .findFirst()
                .orElse(mockData.getTemplates().stream()
                        .filter(t -> t.getId().equals("template-default"))
                        .findFirst()
                        .orElse(null));
    }
}
