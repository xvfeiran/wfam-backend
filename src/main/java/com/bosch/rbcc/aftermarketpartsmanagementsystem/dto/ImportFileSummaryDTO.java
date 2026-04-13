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
@Schema(description = "导入文件维度汇总")
public class ImportFileSummaryDTO {

    @Schema(description = "文件名（相对路径）")
    private String fileName;

    @Schema(description = "总行数")
    private int totalCount;

    @Schema(description = "成功行数")
    private int successCount;

    @Schema(description = "失败行数")
    private int failCount;
}
