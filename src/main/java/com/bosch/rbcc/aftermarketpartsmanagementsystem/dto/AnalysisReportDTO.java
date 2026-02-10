package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReportDTO {
    private String id;
    private String partId;
    private String templateId;
    private Map<String, Object> content;
    private String status;
    private String summary;
    private List<String> attachments;
    private String submittedBy;
    private String submittedAt;
    private String approvedBy;
    private String approvedAt;
    private String createdBy;
    private String createdAt;
}
