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
@Schema(description = "趋势数据点")
public class TrendDataPointDTO {
    @Schema(description = "日期", example = "2024-01-15")
    private String date;
    @Schema(description = "退货单数", example = "5")
    private Integer orders;
    @Schema(description = "附件数", example = "12")
    private Integer parts;
}
