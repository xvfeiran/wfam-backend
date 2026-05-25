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
@Table(name = "APMS_SMB_CONFIGURATION")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmbConfiguration {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "HOST", nullable = false, length = 255)
    private String host;

    @Column(name = "SHARE_NAME", nullable = false, length = 255)
    private String shareName;

    @Column(name = "DOMAIN", length = 100)
    private String domain;

    @Column(name = "SMB_USER", nullable = false, length = 100)
    private String smbUser;

    @Column(name = "SMB_PASSWORD", nullable = false, length = 255)
    private String smbPassword;

    @Column(name = "PREFIX", nullable = false, length = 100)
    private String prefix;

    @Column(name = "ENV", nullable = false, length = 50)
    private String env;

    @Builder.Default
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
}
