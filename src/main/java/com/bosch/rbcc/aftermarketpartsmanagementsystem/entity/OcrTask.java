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
@Table(name = "APMS_OCR_TASK")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrTask {

    public static final String STATUS_CREATED    = "CREATED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS    = "SUCCESS";
    public static final String STATUS_FAILED     = "FAILED";

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    /** 关联的 Part ID，新建 Part 时可为空，编辑时立即绑定 */
    @Column(name = "PART_ID", length = 36)
    private String partId;

    /** 临时文件路径 */
    @Column(name = "FILE_PATH", length = 500)
    private String filePath;

    /** 任务状态：CREATED / PROCESSING / SUCCESS / FAILED */
    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    /** OCR 识别结果 JSON（仅 SUCCESS 时有值） */
    @Column(name = "RESULT_JSON", columnDefinition = "CLOB")
    private String resultJson;

    /** 失败原因（仅 FAILED 时有值） */
    @Column(name = "ERROR_MESSAGE", length = 500)
    private String errorMessage;

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
