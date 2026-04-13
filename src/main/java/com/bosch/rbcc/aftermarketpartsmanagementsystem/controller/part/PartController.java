package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.part;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisReportDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PageResponse;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.AnalysisReportService;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.PartService;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.ReportTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "售后件管理", description = "售后件 CRUD、分析报告及模板匹配")
@RestController
@RequestMapping("/api/v1/parts")
@RequiredArgsConstructor
public class PartController {

    private final PartService partService;
    private final AnalysisReportService analysisReportService;
    private final ReportTemplateService reportTemplateService;

    @Operation(summary = "查询售后件列表（分页）", description = "支持按退货单号、零件码、事业部、产品平台、状态、QC录入、分析员筛选，page从0开始")
    @GetMapping
    public PageResponse<PartDTO> list(
            @Parameter(description = "退货单号（模糊匹配）") @RequestParam(required = false) String orderNumber,
            @Parameter(description = "零件码（模糊匹配）") @RequestParam(required = false) String partCode,
            @Parameter(description = "事业部") @RequestParam(required = false) String businessUnit,
            @Parameter(description = "产品平台") @RequestParam(required = false) String productPlatform,
            @Parameter(description = "售后件状态") @RequestParam(required = false) String status,
            @Parameter(description = "QC录入：yes=已录，no=未录") @RequestParam(required = false) String qcCreated,
            @Parameter(description = "分析员（模糊匹配）") @RequestParam(required = false) String analyst,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                partService.list(orderNumber, partCode, businessUnit, productPlatform, status, qcCreated, analyst, page, size)
        );
    }

    @Operation(summary = "获取售后件详情")
    @GetMapping("/{id}")
    public PartDTO getById(@PathVariable String id) {
        return partService.getById(id);
    }

    @Operation(summary = "新建售后件")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartDTO create(
            @RequestBody PartDTO dto,
            @Parameter(description = "关联的 OCR 任务 ID（新建模式下由前端传入以完成绑定）")
            @RequestParam(value = "ocrTaskId", required = false) String ocrTaskId) {
        return partService.create(dto, ocrTaskId);
    }

    @Operation(summary = "更新售后件")
    @PutMapping("/{id}")
    public PartDTO update(@PathVariable String id, @RequestBody PartDTO dto) {
        return partService.update(id, dto);
    }

    @Operation(summary = "删除售后件")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        partService.delete(id);
    }

    @Operation(summary = "提交售后件，生成件号", description = "生成 {BU}-{PLT}-{seq:04d} 格式的件号")
    @PostMapping("/{id}/submit")
    public PartDTO submit(@PathVariable String id) {
        return partService.submit(id);
    }

    @Operation(summary = "录入 QC No.", description = "仅允许状态为 analysis_completed、scrap_in_progress、scrapped 的售后件录入 QC 单号")
    @PutMapping("/{id}/qc-no")
    public PartDTO updateQcNo(@PathVariable String id, @RequestBody Map<String, String> body) {
        return partService.updateQcNo(id, body.get("qcNo"));
    }

    @Operation(summary = "获取售后件的分析报告")
    @GetMapping("/{id}/reports")
    public List<AnalysisReportDTO> getReports(@PathVariable String id) {
        return analysisReportService.getByPartId(id);
    }

    @Operation(summary = "获取售后件匹配的分析模板", description = "根据产品类别和失效类型匹配模板，无匹配则返回默认模板")
    @GetMapping("/{id}/templates")
    public ReportTemplateDTO getMatchedTemplate(@PathVariable String id) {
        PartDTO part = partService.getById(id);
        return reportTemplateService.matchTemplate(part.getProductPlatform(), part.getFailureType());
    }
}
