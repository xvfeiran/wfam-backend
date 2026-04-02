package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.partcode;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PageDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.PartCode;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.PartCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/part-codes")
@RequiredArgsConstructor
@Tag(name = "Part Code Management", description = "零件号管理API")
public class PartCodeController {

    private final PartCodeService partCodeService;

    @GetMapping
    @Operation(summary = "获取所有零件号（用于下拉选择）")
    public List<PartCode> getAll() {
        return partCodeService.getAll();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询零件号")
    public PageDTO<PartCode> getPage(
            @RequestParam(required = false) String partCode,
            @RequestParam(required = false) String businessUnit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @PageableDefault(size = 10) Pageable pageable) {
        return partCodeService.getPage(partCode, businessUnit, sortBy, sortOrder, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取零件号详情")
    public PartCode getById(@PathVariable String id) {
        return partCodeService.getById(id);
    }

    @PostMapping
    @Operation(summary = "创建零件号")
    public PartCode create(@RequestBody PartCode partCode) {
        return partCodeService.create(partCode);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新零件号")
    public PartCode update(@PathVariable String id, @RequestBody PartCode partCode) {
        return partCodeService.update(id, partCode);
    }
}
