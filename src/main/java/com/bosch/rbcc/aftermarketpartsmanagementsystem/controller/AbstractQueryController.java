package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public abstract class AbstractQueryController {

    protected Pageable toPageable(int page, int size, List<SortItem> sortItems) {
        if (sortItems == null || sortItems.isEmpty()) {
            return PageRequest.of(page, size);
        }
        List<Sort.Order> orders = sortItems.stream()
                .map(s -> new Sort.Order(s.getDirection(), s.getField()))
                .toList();
        return PageRequest.of(page, size, Sort.by(orders));
    }

}
