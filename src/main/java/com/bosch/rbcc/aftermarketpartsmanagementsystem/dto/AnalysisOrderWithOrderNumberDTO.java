package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import java.time.LocalDateTime;

/**
 * DTO for AnalysisOrder with ReturnOrder orderNumber.
 * Used in repository queries to eliminate N+1 problem.
 */
public record AnalysisOrderWithOrderNumberDTO(
    String id,
    String orderId,
    String analyst,
    String status,
    LocalDateTime statusChangedAt,
    String createdBy,
    LocalDateTime createdAt,
    String updatedBy,
    LocalDateTime updatedAt,
    String orderNumber
) {
}
