package com.bosch.rbcc.aftermarketpartsmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "APMS_IMPORT_RECORD")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRecord {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "IMPORT_TYPE", length = 50, nullable = false)
    private String importType;

    @Column(name = "FILE_NAME", length = 255, nullable = false)
    private String fileName;

    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    @Column(name = "TOTAL_COUNT", nullable = false)
    private Integer totalCount;

    @Column(name = "SUCCESS_COUNT", nullable = false)
    private Integer successCount;

    @Column(name = "FAIL_COUNT", nullable = false)
    private Integer failCount;

    @Lob
    @Column(name = "FAIL_LOGS")
    private String failLogs;

    /** 完整导入日志（含成功和失败行的所有信息） */
    @Lob
    @Column(name = "IMPORT_LOGS")
    private String importLogs;

    @CreatedBy
    @Column(name = "CREATED_BY", length = 100, nullable = false, updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
