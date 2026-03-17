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
@Table(name = "APMS_EMAIL_CONFIGURATION")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailConfiguration {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "SMTP_HOST", nullable = false, length = 255)
    private String smtpHost;

    @Column(name = "SMTP_PORT")
    private Integer smtpPort = 25;

    @Column(name = "SMTP_USERNAME", length = 255)
    private String smtpUsername;

    @Column(name = "SMTP_DOMAIN", length = 255)
    private String smtpDomain;

    @Column(name = "EMAIL_FROM", nullable = false, length = 255)
    private String emailFrom;

    @Column(name = "EMAIL_FROM_DISPLAY_NAME", length = 255)
    private String emailFromDisplayName;

    @Column(name = "EMAIL_PASSWORD", nullable = false, length = 255)
    private String emailPassword;

    @Column(name = "ENABLE_SSL")
    private Boolean enableSsl = false;

    @Column(name = "ENABLED")
    private Boolean enabled = true;

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

    /**
     * 获取完整的发件人地址，包含显示名称
     * 例如: "AIoT System Email <Notificationmail.Application@cn.bosch.com>"
     */
    public String getFromAddress() {
        if (emailFromDisplayName != null && !emailFromDisplayName.isBlank()) {
            return String.format("%s <%s>", emailFromDisplayName, emailFrom);
        }
        return emailFrom;
    }
}
