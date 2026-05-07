package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for AnalysisOrder with ReturnOrder orderNumber.
 * Used in repository queries to eliminate N+1 problem.
 *
 * NOTE: Using @Data class instead of record for Hibernate 6.x compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisOrderWithOrderNumberDTO {
    private String id;
    private String orderId;
    private String analyst;
    private String status;
    private LocalDateTime statusChangedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private String orderNumber;
}
