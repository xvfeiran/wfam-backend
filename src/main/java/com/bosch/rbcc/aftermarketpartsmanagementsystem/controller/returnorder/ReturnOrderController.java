package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.returnorder;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "退货单管理", description = "退货单 CRUD 及关联附件查询")
@RestController
@RequestMapping("/api/v1/return-orders")
@RequiredArgsConstructor
public class ReturnOrderController {

    private final MockDataProvider mockData;

    @Operation(summary = "查询退货单列表", description = "支持按单号、客户、状态筛选")
    @GetMapping
    public List<ReturnOrderDTO> list(
            @Parameter(description = "退货单号（模糊匹配）") @RequestParam(required = false) String orderNumber,
            @Parameter(description = "客户名称") @RequestParam(required = false) String customer,
            @Parameter(description = "退货单状态") @RequestParam(required = false) String status) {
        List<ReturnOrderDTO> orders = mockData.getOrders();
        if (orderNumber != null && !orderNumber.isEmpty()) {
            orders = orders.stream()
                    .filter(o -> o.getOrderNumber().toLowerCase().contains(orderNumber.toLowerCase()))
                    .toList();
        }
        if (customer != null && !customer.isEmpty()) {
            orders = orders.stream()
                    .filter(o -> o.getCustomer().equals(customer))
                    .toList();
        }
        if (status != null && !status.isEmpty()) {
            orders = orders.stream()
                    .filter(o -> o.getStatus().equals(status))
                    .toList();
        }
        return orders;
    }

    @Operation(summary = "获取退货单详情")
    @GetMapping("/{id}")
    public ReturnOrderDTO getById(@PathVariable String id) {
        return mockData.getOrders().stream()
                .filter(o -> o.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    @Operation(summary = "新建退货单")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnOrderDTO create(@RequestBody ReturnOrderDTO dto) {
        return dto;
    }

    @Operation(summary = "更新退货单")
    @PutMapping("/{id}")
    public ReturnOrderDTO update(@PathVariable String id, @RequestBody ReturnOrderDTO dto) {
        dto.setId(id);
        return dto;
    }

    @Operation(summary = "删除退货单")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        // mock - no persistence
    }

    @Operation(summary = "获取退货单关联附件列表")
    @GetMapping("/{id}/parts")
    public List<PartDTO> getPartsForOrder(@PathVariable String id) {
        return mockData.getParts().stream()
                .filter(p -> p.getOrderId().equals(id))
                .toList();
    }
}
