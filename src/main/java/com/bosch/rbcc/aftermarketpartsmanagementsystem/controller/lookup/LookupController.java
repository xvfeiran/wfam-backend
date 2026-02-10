package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.lookup;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/lookups")
@RequiredArgsConstructor
public class LookupController {

    private final MockDataProvider mockData;

    @GetMapping
    public Map<String, List<String>> getAllLookups() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("customers", mockData.getCustomers());
        result.put("businessUnits", mockData.getBusinessUnits());
        result.put("productPlatforms", mockData.getProductPlatforms());
        result.put("failureTypes", mockData.getFailureTypes());
        return result;
    }

    @GetMapping("/{type}")
    public List<String> getLookup(@PathVariable String type) {
        return switch (type) {
            case "customers" -> mockData.getCustomers();
            case "business-units" -> mockData.getBusinessUnits();
            case "product-platforms" -> mockData.getProductPlatforms();
            case "failure-types" -> mockData.getFailureTypes();
            default -> List.of();
        };
    }
}
