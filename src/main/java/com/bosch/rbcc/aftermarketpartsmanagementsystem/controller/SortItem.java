package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import lombok.Data;
import org.springframework.data.domain.Sort;

@Data
public class SortItem {
    private String field;
    private Sort.Direction direction = Sort.Direction.ASC;
}
