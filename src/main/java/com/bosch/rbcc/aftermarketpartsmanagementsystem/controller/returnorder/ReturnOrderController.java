package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.returnorder;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/return-orders")
@RequiredArgsConstructor
public class ReturnOrderController {

    private final MockDataProvider mockData;

    @GetMapping
    public List<ReturnOrderDTO> list(
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String status) {
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

    @GetMapping("/{id}")
    public ReturnOrderDTO getById(@PathVariable String id) {
        return mockData.getOrders().stream()
                .filter(o -> o.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnOrderDTO create(@RequestBody ReturnOrderDTO dto) {
        return dto;
    }

    @PutMapping("/{id}")
    public ReturnOrderDTO update(@PathVariable String id, @RequestBody ReturnOrderDTO dto) {
        dto.setId(id);
        return dto;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        // mock - no persistence
    }

    @GetMapping("/{id}/parts")
    public List<PartDTO> getPartsForOrder(@PathVariable String id) {
        return mockData.getParts().stream()
                .filter(p -> p.getOrderId().equals(id))
                .toList();
    }
}
