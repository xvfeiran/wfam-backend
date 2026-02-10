package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapApplicationDTO {
    private String id;
    private String orderNumber;
    private String partNumbers;
    private int quantity;
    private String applicant;
    private String approver;
    private String applyTime;
    private String approveTime;
    private String status;
    private String reason;
    private String rejectReason;
}
