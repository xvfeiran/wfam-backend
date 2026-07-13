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
@Table(name = "APMS_ANALYSIS_REPORT")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisReport {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "PART_ID", length = 36, nullable = false)
    private String partId;

    @Column(name = "TEMPLATE_ID", length = 36, nullable = false)
    private String templateId;

    @Lob
    @Column(name = "CONTENT", columnDefinition = "CLOB")
    private String content;

    @Column(name = "SUMMARY", length = 500)
    private String summary;

    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    @Column(name = "ATTACHMENTS", length = 1000)
    private String attachments;

    @Column(name = "SUBMITTED_BY", length = 100)
    private String submittedBy;

    @Column(name = "SUBMITTED_AT")
    private LocalDateTime submittedAt;

    @Column(name = "APPROVED_BY", length = 100)
    private String approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDateTime approvedAt;

    @Column(name = "REJECT_REASON", length = 500)
    private String rejectReason;

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
