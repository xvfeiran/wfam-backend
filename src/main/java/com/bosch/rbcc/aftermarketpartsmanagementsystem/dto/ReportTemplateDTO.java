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
@Schema(description = "分析报告模板")
public class ReportTemplateDTO {
    @Schema(description = "模板ID", example = "template-1")
    private String id;
    @Schema(description = "模板名称", example = "WSA噪音分析模板")
    private String name;
    @Schema(description = "适用产品类别", example = "WSA")
    private String productCategory;
    @Schema(description = "适用失效类型", example = "噪音")
    private String failureType;
    private List<ReportTemplateFieldDTO> fields;
    @Schema(description = "创建时间")
    private String createdAt;
    @Schema(description = "创建人")
    private String createdBy;
}
