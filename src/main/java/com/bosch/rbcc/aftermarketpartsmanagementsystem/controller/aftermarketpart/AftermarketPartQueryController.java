package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.aftermarketpart;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.AbstractQueryController;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AftermarketPart;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.PartQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//@RestController
@RequestMapping("/parts")
@RequiredArgsConstructor
public class AftermarketPartQueryController extends AbstractQueryController {

    private final PartQueryService service;

    @PostMapping("/search")
    public Page<AftermarketPart> search(@RequestBody AftermarketPartSearchRequest request) {
        Pageable pageable = toPageable(request.getPage(), request.getSize(), request.getSort());
        return service.search(request.getCondition(), pageable);
    }
}
