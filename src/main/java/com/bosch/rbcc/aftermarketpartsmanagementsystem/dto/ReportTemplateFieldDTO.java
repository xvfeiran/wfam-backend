package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模板字段定义")
public class ReportTemplateFieldDTO {
    @Schema(description = "字段名", example = "noiseType")
    private String name;
    @Schema(description = "字段类型: text/textarea/select/date/number", example = "select")
    private String type;
    @Schema(description = "显示标签", example = "噪音类型")
    private String label;
    @Schema(description = "是否必填", example = "true")
    private boolean required;
    private List<String> options;
}
