package com.bosch.rbcc.aftermarketpartsmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 导入日志明细实体
 *
 * 用于存储导入过程中的详细日志，替代 ImportRecord 中 CLOB 字段的 JSON 数据。
 * 这样可以避免频繁更新 CLOB 字段导致的性能问题。
 */
@Entity
@Table(name = "APMS_IMPORT_LOG_DETAIL")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportLogDetail {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "IMPORT_ID", length = 36, nullable = false)
    private String importId;

    @Column(name = "FILE_NAME", length = 255)
    private String fileName;

    @Column(name = "ROW_NUMBER")
    private Integer rowNumber;

    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    @Column(name = "ERROR_CODE", length = 50)
    private String errorCode;

    @Column(name = "ERROR_MESSAGE", length = 500)
    private String errorMessage;

    @Lob
    @Column(name = "RAW_DATA")
    private String rawData;

    @Column(name = "ORDER_ID", length = 36)
    private String orderId;

    @Column(name = "ORDER_NUMBER", length = 50)
    private String orderNumber;

    @Column(name = "ORDER_CREATED", length = 1)
    private String orderCreated;

    @Column(name = "PART_ID", length = 36)
    private String partId;

    @Column(name = "PART_CODE", length = 50)
    private String partCode;

    @Column(name = "PART_NUMBER", length = 50)
    private String partNumber;

    @Column(name = "QC_NO", length = 50)
    private String qcNo;

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
