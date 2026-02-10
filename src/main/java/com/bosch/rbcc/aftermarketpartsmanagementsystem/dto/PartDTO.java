package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartDTO {
    private String id;
    private String partNumber;
    private String orderId;
    private String orderNumber;
    private String partCode;
    private String businessUnit;
    private String productPlatform;
    private String productionShift;
    private String failureType;
    private String vehicleProductionDate;
    private String vehiclePurchaseDate;
    private String vehicleFailureDate;
    private String vehicleVIN;
    private Integer vehicleMileage;
    private String customerDescription;
    private String otherDescription;
    private String status;
    private List<String> images;
    private String createdBy;
    private String createdAt;
    private String updatedBy;
    private String updatedAt;
}
