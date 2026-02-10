package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.aftermarketpart;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AftermarketPartSearchCondition {

    private String brand;
    private String status;

    private BigDecimal priceMin;
    private BigDecimal priceMax;

    private List<Long> categoryIds;
}
