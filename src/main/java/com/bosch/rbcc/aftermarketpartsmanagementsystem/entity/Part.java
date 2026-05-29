package com.bosch.rbcc.aftermarketpartsmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "APMS_PART")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Part {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "PART_NUMBER", length = 30, unique = true)
    private String partNumber;

    @Column(name = "ORDER_ID", length = 36, nullable = false)
    private String orderId;

    @Column(name = "PART_CODE", length = 50, nullable = false)
    private String partCode;

    @Column(name = "BUSINESS_UNIT", length = 50, nullable = false)
    private String businessUnit;

    @Column(name = "PRODUCT_PLATFORM", length = 50, nullable = false)
    private String productPlatform;

    @Column(name = "PART_PRODUCTION_DATE")
    private LocalDate partProductionDate;

    @Column(name = "PRODUCTION_SHIFT", length = 20)
    private String productionShift;

    // COMPLAINT_TYPE 列保留（不使用 Java 字段），前端/后端不再映射
    @Column(name = "CUSTOMER_FAILURE_TYPE", length = 50)
    private String failureType;

    @Column(name = "BOSCH_FAILURE_TYPE", length = 20)
    private String boschFailureType;

    @Column(name = "VEHICLE_PRODUCTION_DATE")
    private LocalDate vehicleProductionDate;

    @Column(name = "VEHICLE_PURCHASE_DATE")
    private LocalDate vehiclePurchaseDate;

    @Column(name = "VEHICLE_FAILURE_DATE")
    private LocalDate vehicleFailureDate;

    @Column(name = "VEHICLE_VIN", length = 255)
    private String vehicleVin;

    @Column(name = "VEHICLE_MILEAGE")
    private Integer vehicleMileage;

    @Column(name = "CUSTOMER_DESCRIPTION", length = 500)
    private String customerDescription;

    @Column(name = "OTHER_DESCRIPTION", length = 500)
    private String otherDescription;

    @Column(name = "OTHER_INFO", length = 500)
    private String otherInfo;

    @Column(name = "REPAIR_STATION", length = 100)
    private String repairStation;

    @Column(name = "COMPLAINT_LOCATION", length = 100)
    private String complaintLocation;

    @Column(name = "RESPONSIBLE_ENGINEER", length = 100)
    private String responsibleEngineer;

    @Column(name = "ANALYST", length = 100)
    private String analyst;

    @Column(name = "QC_NO", length = 50)
    private String qcNo;

    @Column(name = "IS_SAMPLE")
    @Builder.Default
    private Integer isSample = 0;

    @Column(name = "STATUS_CHANGED_AT")
    private LocalDateTime statusChangedAt;

    @Column(name = "IMAGES", length = 2000)
    private String images;

    @Column(name = "STATUS", length = 50, nullable = false)
    private String status;

    @CreatedBy
    @Column(name = "CREATED_BY", length = 100, nullable = false, updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedBy
    @Column(name = "UPDATED_BY", length = 100)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
