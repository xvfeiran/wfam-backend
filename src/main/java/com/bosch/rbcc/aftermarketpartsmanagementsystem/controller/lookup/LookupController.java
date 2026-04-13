package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.lookup;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Customer;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.CustomerService;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.PartCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "数据字典", description = "客户、事业部、产品平台、产品类别、失效类型等下拉选项数据")
@RestController
@RequestMapping("/api/v1/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final CustomerService customerService;
    private final PartCodeService partCodeService;
    private final PartRepository partRepository;
    private final ReturnOrderRepository returnOrderRepository;

    @Operation(summary = "获取所有下拉选项数据")
    @GetMapping
    public Map<String, List<String>> getAllLookups() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("customers", getCustomers());
        result.put("businessUnits", partCodeService.getDistinctBusinessUnits());
        result.put("productPlatforms", partCodeService.getDistinctProductPlatforms());
        result.put("productCategories", List.of());
        result.put("failureTypes", getFailureTypes());
        return result;
    }

    @Operation(summary = "获取指定类型的下拉选项", description = "type可选值: customers, business-units, product-platforms, product-categories, failure-types")
    @GetMapping("/{type}")
    public List<String> getLookup(@Parameter(description = "数据类型") @PathVariable String type) {
        return switch (type) {
            case "customers" -> getCustomers();
            case "business-units" -> partCodeService.getDistinctBusinessUnits();
            case "product-platforms" -> partCodeService.getDistinctProductPlatforms();
            case "product-categories" -> List.of();
            case "failure-types" -> getFailureTypes();
            default -> List.of();
        };
    }

    private List<String> getCustomers() {
        return customerService.getAll().stream()
            .map(Customer::getName)
            .filter(Objects::nonNull)
            .filter(s -> !s.isBlank())
            .sorted()
            .toList();
    }

    private List<String> getFailureTypes() {
        List<String> fromPart = partRepository.findDistinctFailureTypes();
        if (!fromPart.isEmpty()) {
            return fromPart;
        }
        return returnOrderRepository.findDistinctComplaintTypes();
    }
}
