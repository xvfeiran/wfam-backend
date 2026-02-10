package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.approval;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisApplicationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ScrapApplicationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "审批管理", description = "报废申请、分析报告审批流程")
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final MockDataProvider mockData;

    @Operation(summary = "获取我的报废申请")
    @GetMapping("/my/scrap")
    public List<ScrapApplicationDTO> getMyScrapApplications() {
        return mockData.getMyScrapApplications();
    }

    @Operation(summary = "获取我的分析报告申请")
    @GetMapping("/my/analysis")
    public List<AnalysisApplicationDTO> getMyAnalysisApplications() {
        return mockData.getMyAnalysisApplications();
    }

    @Operation(summary = "获取待审批的报废申请")
    @GetMapping("/pending/scrap")
    public List<ScrapApplicationDTO> getPendingScrapApprovals() {
        return mockData.getPendingScrapApprovals();
    }

    @Operation(summary = "获取待审批的分析报告")
    @GetMapping("/pending/analysis")
    public List<AnalysisApplicationDTO> getPendingAnalysisApprovals() {
        return mockData.getPendingAnalysisApprovals();
    }

    @Operation(summary = "审批通过")
    @PostMapping("/{id}/approve")
    public void approve(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        // mock - no persistence
    }

    @Operation(summary = "审批驳回")
    @PostMapping("/{id}/reject")
    public void reject(@PathVariable String id, @RequestBody Map<String, String> body) {
        // mock - no persistence
    }

    @Operation(summary = "撤回申请")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable String id) {
        // mock - no persistence
    }
}
