package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisApplicationDTO {
    private String id;
    private String reportNumber;
    private String partNumber;
    private String productPlatform;
    private String failureType;
    private String submitter;
    private String approver;
    private String submitTime;
    private String approveTime;
    private String status;
    private String summary;
    private Map<String, String> content;
}
