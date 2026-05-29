package com.bosch.rbcc.aftermarketpartsmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "APMS_ANALYSIS_ORDER")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisOrder {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "ORDER_ID", length = 36, nullable = false)
    private String orderId;

    @Column(name = "ANALYST", length = 100, nullable = false)
    private String analyst;

    @Column(name = "STATUS", length = 50, nullable = false)
    private String status;

    @Column(name = "WORKON_SCRAP_NO", length = 100)
    private String workonScrapNo;

    @Column(name = "STATUS_CHANGED_AT")
    private LocalDateTime statusChangedAt;

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
