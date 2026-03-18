package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.AnalysisOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
// Note: CommonHeaders.getRoleNames() returns comma-separated string

@Tag(name = "分析单管理", description = "分析单 CRUD 及抽样/报废操作")
@RestController
@RequestMapping("/api/v1/analysis-orders")
@RequiredArgsConstructor
public class AnalysisOrderController {

    private final AnalysisOrderService analysisOrderService;

    @Operation(summary = "查询分析单列表")
    @GetMapping
    public List<AnalysisOrderDTO> list() {
        var headers = CommonHeaderManager.getCommonHeaders();
        String loginName = headers != null ? headers.getNtAccount() : null;
        String roleNamesStr = headers != null ? headers.getRoleNames() : null;
        return analysisOrderService.list(loginName, roleNamesStr);
    }

    @Operation(summary = "获取分析单详情（含关联售后件）")
    @GetMapping("/{id}")
    public AnalysisOrderDTO getById(@PathVariable String id) {
        return analysisOrderService.getById(id);
    }

    @Operation(summary = "抽样确认", description = "pending_sampling → in_detailed_analysis")
    @PostMapping("/{id}/sampling")
    public AnalysisOrderDTO sampling(@PathVariable String id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> sampledPartIds = (List<String>) body.get("sampledPartIds");
        return analysisOrderService.sampling(id, sampledPartIds);
    }

    @Operation(summary = "提交报废申请", description = "→ workon_scrap_in_progress")
    @PostMapping("/{id}/scrap")
    public AnalysisOrderDTO scrap(@PathVariable String id) {
        return analysisOrderService.scrap(id);
    }

    @Operation(summary = "确认 WorkOn 完成", description = "workon_scrap_in_progress → workon_scrapped")
    @PostMapping("/{id}/scrap/workon-confirm")
    public AnalysisOrderDTO workonConfirm(@PathVariable String id) {
        return analysisOrderService.workonConfirm(id);
    }
}
