package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "WorkON报废确认请求")
public class WorkonConfirmRequest {
    @NotBlank(message = "报废单号不能为空")
    @Schema(description = "WorkON报废单号", required = true)
    private String workonScrapNo;
}
