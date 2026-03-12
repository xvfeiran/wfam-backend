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
@Schema(description = "分页结果")
public class PageDTO<T> {
    @Schema(description = "数据列表")
    private List<T> data;

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页码（从1开始）")
    private int page;

    @Schema(description = "每页大小")
    private int pageSize;

    @Schema(description = "总页数")
    private int totalPages;

    /**
     * 从 Spring Data Page 对象创建 PageDTO
     */
    public static <T> PageDTO<T> of(org.springframework.data.domain.Page<T> page) {
        return PageDTO.<T>builder()
                .data(page.getContent())
                .total(page.getTotalElements())
                .page(page.getNumber() + 1) // Spring Data pages are 0-based, convert to 1-based
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }
}
