package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.aftermarketpart.AftermarketPartSearchCondition;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AftermarketPart;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AftermarketPartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AftermarketPartSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PartQueryService {

    private final AftermarketPartRepository repository;

    public Page<AftermarketPart> search(AftermarketPartSearchCondition condition, Pageable pageable) {
        return repository.findAll(
                AftermarketPartSpecifications.byCondition(condition),
                pageable
        );
    }
}
