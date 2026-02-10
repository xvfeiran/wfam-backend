package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnOrderDTO {
    private String id;
    private String orderNumber;
    private String customer;
    private String receiveDate;
    private String complaintDate;
    private String returnMethod;
    private String trackingNumber;
    private int returnQuantity;
    private int initialAnalysisQuantity;
    private int detailedAnalysisQuantity;
    private int scrappedQuantity;
    private int qcCreatedQuantity;
    private int qcNotCreatedQuantity;
    private String description;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
}
