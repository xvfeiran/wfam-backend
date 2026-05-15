package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.approval;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisApplicationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaders;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.AnalysisReportService;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Tag(name = "审批管理", description = "分析报告审批流程")
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private static final String ROLE_QMC_LEADER = "W_RBCC_AEP_WFAM_QMC_Leader";

    private final ApprovalService approvalService;
    private final AnalysisReportService analysisReportService;

    @Operation(summary = "获取我的分析报告申请")
    @GetMapping("/my/analysis")
    public List<AnalysisApplicationDTO> getMyAnalysisApplications() {
        return approvalService.getMyAnalysisApplications(getCurrentUsername());
    }

    @Operation(summary = "获取待审批的分析报告")
    @GetMapping("/pending/analysis")
    public List<AnalysisApplicationDTO> getPendingAnalysisApprovals() {
        requireQMCLeader();
        return approvalService.getPendingAnalysisApprovals();
    }

    @Operation(summary = "审批通过")
    @PostMapping("/{id}/approve")
    public void approve(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        requireQMCLeader();
        String comment = body != null ? body.get("comment") : null;
        analysisReportService.approve(id, getCurrentUsername(), comment);
    }

    @Operation(summary = "审批驳回")
    @PostMapping("/{id}/reject")
    public void reject(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        requireQMCLeader();
        analysisReportService.reject(id, getCurrentUsername(), body.get("reason"));
    }

    @Operation(summary = "撤回申请")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable String id) {
        analysisReportService.withdraw(id, getCurrentUsername());
    }

    private String getCurrentUsername() {
        CommonHeaders headers = CommonHeaderManager.getCommonHeaders();
        return headers != null && headers.getUsername() != null ? headers.getUsername() : "anonymous";
    }

    private boolean hasRole(String roleName) {
        CommonHeaders headers = CommonHeaderManager.getCommonHeaders();
        if (headers == null || headers.getRoleNames() == null) {
            return false;
        }
        return headers.getRoleNames().contains(roleName);
    }

    private void requireQMCLeader() {
        if (!hasRole(ROLE_QMC_LEADER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only QMC Leader can access pending approvals");
        }
    }
}
