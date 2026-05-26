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
@Table(name = "APMS_REPORT_TEMPLATE")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportTemplate {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "NAME", length = 200, nullable = false)
    private String name;

    @Column(name = "PRODUCT_PLATFORM", length = 50)
    private String productPlatform;

    @Column(name = "FAILURE_TYPE", length = 50)
    private String failureType;

    @Column(name = "FILE_PATH", length = 500, nullable = false)
    private String filePath;

    @Column(name = "FILE_NAME", length = 200, nullable = false)
    private String fileName;

    @Lob
    @Column(name = "FIELD_DEFINITIONS")
    private String fieldDefinitions;

    @Column(name = "ENABLED")
    @Builder.Default
    private Integer enabled = 1;

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
