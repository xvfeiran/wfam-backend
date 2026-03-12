package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.customer;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PageDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Customer;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "客户管理API")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @Operation(summary = "获取所有客户（用于下拉选择）", description = "获取所有客户列表，不分页")
    public List<Customer> getAll() {
        return customerService.getAll();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询客户", description = "分页查询客户列表，支持按名称或代码排序")
    public PageDTO<Customer> getPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @PageableDefault(size = 10) Pageable pageable) {
        return customerService.getPage(name, code, sortBy, sortOrder, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取客户详情", description = "根据ID获取客户详情")
    public Customer getById(@PathVariable String id) {
        return customerService.getById(id);
    }

    @PostMapping
    @Operation(summary = "创建客户", description = "创建新客户")
    public Customer create(@RequestBody Customer customer) {
        return customerService.create(customer);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新客户", description = "更新客户信息")
    public Customer update(@PathVariable String id, @RequestBody Customer customer) {
        return customerService.update(id, customer);
    }
}
