package com.bosch.rbcc.aftermarketpartsmanagementsystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic page response wrapper for stable JSON serialization.
 * Replaces direct PageImpl serialization to avoid Spring Data warnings.
 *
 * @param <T> The type of data in the page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应")
public class PageResponse<T> {

    @Schema(description = "数据列表")
    private List<T> data;

    @Schema(description = "总记录数", example = "100")
    private long total;

    @Schema(description = "当前页码（从1开始）", example = "1")
    private int page;

    @Schema(description = "每页大小", example = "10")
    private int size;

    @Schema(description = "总页数", example = "10")
    private int totalPages;

    /**
     * Create a PageResponse from Spring Data Page.
     * Converts 0-based page to 1-based page for frontend compatibility.
     */
    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return PageResponse.<T>builder()
                .data(page.getContent())
                .total(page.getTotalElements())
                .page(page.getNumber() + 1) // Convert 0-based to 1-based
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .build();
    }
}
