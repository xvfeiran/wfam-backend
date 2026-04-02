package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PageDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.PartCode;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartCodeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartCodeService {

    private final PartCodeRepository partCodeRepository;

    public List<PartCode> getAll() {
        return partCodeRepository.findAll(Sort.by(Sort.Order.asc("partCode")));
    }

    public PageDTO<PartCode> getPage(String partCode, String businessUnit, String sortBy, String sortOrder, Pageable pageable) {
        Pageable sortedPageable = pageable;
        if (sortBy != null && !sortBy.isBlank()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
            Sort sort = Sort.by(direction, sortBy);
            sortedPageable = pageable.getSort().isUnsorted()
                ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(pageable.getSort()));
        }

        Page<PartCode> page = partCodeRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (partCode != null && !partCode.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("partCode")), "%" + partCode.toUpperCase() + "%"));
            }
            if (businessUnit != null && !businessUnit.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("businessUnit")), "%" + businessUnit.toUpperCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, sortedPageable);
        return PageDTO.of(page);
    }

    public PartCode getById(String id) {
        return partCodeRepository.findById(id).orElse(null);
    }

    /**
     * 根据零件号精确查询（用于售后件表单自动填充产品类型和BU）
     */
    public PartCode getByPartCode(String partCode) {
        return partCodeRepository.findByPartCode(partCode).orElse(null);
    }

    @Transactional
    public PartCode create(PartCode partCode) {
        if (partCodeRepository.existsByPartCode(partCode.getPartCode())) {
            throw new IllegalArgumentException("Part code already exists: " + partCode.getPartCode());
        }
        partCode.setId(UUID.randomUUID().toString());
        return partCodeRepository.save(partCode);
    }

    @Transactional
    public PartCode update(String id, PartCode partCode) {
        PartCode existing = partCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Part code not found: " + id));

        if (!existing.getPartCode().equals(partCode.getPartCode()) &&
                partCodeRepository.existsByPartCode(partCode.getPartCode())) {
            throw new IllegalArgumentException("Part code already exists: " + partCode.getPartCode());
        }

        existing.setPartCode(partCode.getPartCode());
        existing.setBusinessUnit(partCode.getBusinessUnit());
        existing.setProductPlatform(partCode.getProductPlatform());
        return partCodeRepository.save(existing);
    }
}
