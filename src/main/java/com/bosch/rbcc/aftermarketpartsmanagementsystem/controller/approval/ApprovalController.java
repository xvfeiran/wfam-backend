package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.approval;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisApplicationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ScrapApplicationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final MockDataProvider mockData;

    @GetMapping("/my/scrap")
    public List<ScrapApplicationDTO> getMyScrapApplications() {
        return mockData.getMyScrapApplications();
    }

    @GetMapping("/my/analysis")
    public List<AnalysisApplicationDTO> getMyAnalysisApplications() {
        return mockData.getMyAnalysisApplications();
    }

    @GetMapping("/pending/scrap")
    public List<ScrapApplicationDTO> getPendingScrapApprovals() {
        return mockData.getPendingScrapApprovals();
    }

    @GetMapping("/pending/analysis")
    public List<AnalysisApplicationDTO> getPendingAnalysisApprovals() {
        return mockData.getPendingAnalysisApprovals();
    }

    @PostMapping("/{id}/approve")
    public void approve(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        // mock - no persistence
    }

    @PostMapping("/{id}/reject")
    public void reject(@PathVariable String id, @RequestBody Map<String, String> body) {
        // mock - no persistence
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable String id) {
        // mock - no persistence
    }
}
