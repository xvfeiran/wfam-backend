package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "邮件服务器配置")
public class EmailConfigurationDTO {
    @Schema(description = "配置ID", example = "email-config-1")
    private String id;

    @Schema(description = "SMTP服务器地址", example = "rb-smtp-auth.rbesz01.com")
    private String smtpHost;

    @Schema(description = "SMTP服务器端口", example = "25")
    private Integer smtpPort;

    @Schema(description = "SMTP认证用户名", example = "RAC1CNG")
    private String smtpUsername;

    @Schema(description = "SMTP认证域", example = "rb-smtp-auth.rbesz01.com")
    private String smtpDomain;

    @Schema(description = "发件人邮箱", example = "Notificationmail.Application@cn.bosch.com")
    private String emailFrom;

    @Schema(description = "发件人显示名称", example = "AIoT System Email")
    private String emailFromDisplayName;

    @Schema(description = "邮箱密码")
    private String emailPassword;

    @Schema(description = "是否启用SSL", example = "false")
    private Boolean enableSsl;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private String createdAt;

    @Schema(description = "创建人")
    private String createdBy;
}
