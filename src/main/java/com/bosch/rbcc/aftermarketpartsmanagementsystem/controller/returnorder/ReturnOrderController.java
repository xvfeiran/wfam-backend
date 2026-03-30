package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.returnorder;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PageResponse;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.ReturnOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
// sampling/scrap endpoints removed in v3.0

@Tag(name = "退货单管理", description = "退货单 CRUD 及关联售后件查询")
@RestController
@RequestMapping("/api/v1/return-orders")
@RequiredArgsConstructor
public class ReturnOrderController {

    private final ReturnOrderService returnOrderService;

    @Operation(summary = "查询退货单列表", description = "支持按单号、客户、状态、日期范围筛选，支持分页和排序")
    @GetMapping
    public PageResponse<ReturnOrderDTO> list(
            @Parameter(description = "退货单号（模糊匹配）") @RequestParam(required = false) String orderNumber,
            @Parameter(description = "客户名称") @RequestParam(required = false) String customer,
            @Parameter(description = "退货单状态") @RequestParam(required = false) String status,
            @Parameter(description = "收件日期开始") @RequestParam(required = false) String receiveDateStart,
            @Parameter(description = "收件日期结束") @RequestParam(required = false) String receiveDateEnd,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ReturnOrderDTO> page = returnOrderService.list(orderNumber, customer, status, receiveDateStart, receiveDateEnd, pageable);
        return PageResponse.of(page);
    }

    @Operation(summary = "获取退货单详情")
    @GetMapping("/{id}")
    public ReturnOrderDTO getById(@PathVariable String id) {
        return returnOrderService.getById(id);
    }

    @Operation(summary = "新建退货单")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnOrderDTO create(@Valid @RequestBody ReturnOrderDTO dto) {
        return returnOrderService.create(dto);
    }

    @Operation(summary = "更新退货单")
    @PutMapping("/{id}")
    public ReturnOrderDTO update(@PathVariable String id, @Valid @RequestBody ReturnOrderDTO dto) {
        return returnOrderService.update(id, dto);
    }

    @Operation(summary = "删除退货单", description = "支持级联删除关联售后件")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable String id,
        @Parameter(description = "是否级联删除关联售后件") @RequestParam(required = false, defaultValue = "false") boolean cascade
    ) {
        returnOrderService.delete(id, cascade);
    }

    @Operation(summary = "获取退货单关联的售后件数量")
    @GetMapping("/{id}/parts-count")
    public Map<String, Long> getPartsCount(@PathVariable String id) {
        long count = returnOrderService.getPartsCount(id);
        return Map.of("partsCount", count);
    }

    @Operation(summary = "提交退货单，生成退货单号", description = "draft → in_initial_analysis")
    @PostMapping("/{id}/submit")
    public ReturnOrderDTO submit(@PathVariable String id) {
        return returnOrderService.submit(id);
    }

    @Operation(summary = "获取退货单关联售后件列表（分页）", description = "支持按关键词、事业部、产品平台、状态筛选，支持分页和排序")
    @GetMapping("/{id}/parts")
    public PageResponse<PartDTO> getPartsForOrder(
            @PathVariable String id,
            @Parameter(description = "关键词（模糊匹配零件号和零件码）") @RequestParam(required = false) String keyword,
            @Parameter(description = "事业部") @RequestParam(required = false) String businessUnit,
            @Parameter(description = "产品平台") @RequestParam(required = false) String productPlatform,
            @Parameter(description = "售后件状态") @RequestParam(required = false) String status,
            @Parameter(description = "分析师") @RequestParam(required = false) String analyst,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PartDTO> page = returnOrderService.getPartsForOrder(id, keyword, businessUnit, productPlatform, status, analyst, pageable);
        return PageResponse.of(page);
    }

    @Operation(summary = "导出退货单列表为 Excel")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String receiveDateStart,
            @RequestParam(required = false) String receiveDateEnd) {
        byte[] data = returnOrderService.exportToExcel(orderNumber, customer, status, receiveDateStart, receiveDateEnd);
        String filename = "ReturnOrders_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @Operation(summary = "导入退货单（Excel）")
    @PostMapping("/import")
    public Map<String, Integer> importOrders(@RequestParam("file") MultipartFile file) {
        return returnOrderService.importFromExcel(file);
    }
}
