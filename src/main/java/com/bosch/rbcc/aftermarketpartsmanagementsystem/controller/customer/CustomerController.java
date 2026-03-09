package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.customer;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Customer;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "客户管理API")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @Operation(summary = "获取所有客户", description = "获取所有客户列表")
    public List<Customer> getAll() {
        return customerService.getAll();
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

    @DeleteMapping("/{id}")
    @Operation(summary = "删除客户", description = "删除客户")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
