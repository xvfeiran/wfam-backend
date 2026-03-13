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
@Schema(description = "导入记录")
public class ImportRecordDTO {

    @Schema(description = "记录ID")
    private String id;

    @Schema(description = "导入类型", example = "return_order")
    private String importType;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "状态", example = "completed")
    private String status;

    @Schema(description = "总行数")
    private Integer totalCount;

    @Schema(description = "成功行数")
    private Integer successCount;

    @Schema(description = "失败行数")
    private Integer failCount;

    @Schema(description = "失败日志（JSON数组）")
    private String failLogs;

    @Schema(description = "完整导入日志（成功+失败，JSON数组）")
    private String importLogs;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private String createdAt;
}
