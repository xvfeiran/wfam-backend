package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.aftermarketpart;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.SortItem;
import lombok.Data;

import java.util.List;

@Data
public class AftermarketPartSearchRequest {

    private AftermarketPartSearchCondition condition;

    private int page = 0;
    private int size = 20;

    private List<SortItem> sort = List.of();
}

