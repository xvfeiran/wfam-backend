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
@Schema(description = "SMB存储配置")
public class SmbConfigurationDTO {

    @Schema(description = "配置ID（固定为 smb-config-singleton）")
    private String id;

    @Schema(description = "SMB服务器地址", example = "cng0fs02.apac.bosch.com")
    private String host;

    @Schema(description = "共享名", example = "superlineleader$")
    private String shareName;

    @Schema(description = "域", example = "APAC")
    private String domain;

    @Schema(description = "用户名", example = "XEF1CNG")
    private String user;

    @Schema(description = "密码（GET时返回******）")
    private String password;

    @Schema(description = "路径前缀", example = "wfam")
    private String prefix;

    @Schema(description = "环境标识", example = "dev")
    private String env;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "最后更新时间（yyyy-MM-dd HH:mm:ss）")
    private String updatedAt;

    @Schema(description = "最后更新人")
    private String updatedBy;
}
