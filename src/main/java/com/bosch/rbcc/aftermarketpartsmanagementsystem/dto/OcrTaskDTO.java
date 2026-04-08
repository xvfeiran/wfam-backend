package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "OCR 任务状态")
public class OcrTaskDTO {

    @Schema(description = "任务 ID")
    private String taskId;

    @Schema(description = "关联 Part ID（可为空）")
    private String partId;

    @Schema(description = "任务状态: CREATED / PROCESSING / SUCCESS / FAILED")
    private String status;

    @Schema(description = "OCR 识别结果（status=SUCCESS 时有值）")
    private OcrResultDTO result;

    @Schema(description = "失败原因（status=FAILED 时有值）")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
