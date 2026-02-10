package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.part;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisReportDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parts")
@RequiredArgsConstructor
public class PartController {

    private final MockDataProvider mockData;

    @GetMapping
    public List<PartDTO> list(
            @RequestParam(required = false) String partNumber,
            @RequestParam(required = false) String partCode,
            @RequestParam(required = false) String businessUnit,
            @RequestParam(required = false) String productPlatform,
            @RequestParam(required = false) String status) {
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

    @GetMapping("/{id}")
    public PartDTO getById(@PathVariable String id) {
        return mockData.getParts().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartDTO create(@RequestBody PartDTO dto) {
        return dto;
    }

    @PutMapping("/{id}")
    public PartDTO update(@PathVariable String id, @RequestBody PartDTO dto) {
        dto.setId(id);
        return dto;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        // mock - no persistence
    }

    @GetMapping("/{id}/reports")
    public List<AnalysisReportDTO> getReports(@PathVariable String id) {
        return mockData.getReports().stream()
                .filter(r -> r.getPartId().equals(id))
                .toList();
    }

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
