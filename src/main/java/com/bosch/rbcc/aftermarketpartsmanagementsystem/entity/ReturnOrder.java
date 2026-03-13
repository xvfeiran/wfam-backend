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
@Table(name = "APMS_RETURN_ORDER")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnOrder {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "ORDER_NUMBER", length = 20, unique = true)
    private String orderNumber;

    @Column(name = "CUSTOMER_ID", length = 36)
    private String customerId;

    // 保留用于显示和兼容
    @Column(name = "CUSTOMER", length = 100)
    private String customer;

    @Column(name = "RECEIVE_DATE", nullable = false)
    private LocalDate receiveDate;

    @Column(name = "COMPLAINT_DATE", nullable = false)
    private LocalDate complaintDate;

    @Column(name = "RETURN_METHOD", length = 20, nullable = false)
    private String returnMethod;

    @Column(name = "TRACKING_NUMBER", length = 50)
    private String trackingNumber;

    @Column(name = "RETURN_QUANTITY", nullable = false)
    private Integer returnQuantity;

    @Column(name = "FAILURE_TYPE", length = 20, nullable = false)
    private String failureType;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

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
