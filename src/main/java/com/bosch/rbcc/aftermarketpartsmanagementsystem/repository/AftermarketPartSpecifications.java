package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.aftermarketpart.AftermarketPartSearchCondition;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AftermarketPart;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AftermarketPartSpecifications {

    public static Specification<AftermarketPart> byCondition(AftermarketPartSearchCondition cond) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (cond == null) {
                return cb.conjunction();
            }

            if (cond.getBrand() != null && !cond.getBrand().isBlank()) {
                predicates.add(cb.equal(root.get("brand"), cond.getBrand()));
            }

            if (cond.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), cond.getStatus()));
            }

            if (cond.getPriceMin() != null) {
                predicates.add(cb.ge(root.get("price"), cond.getPriceMin()));
            }

            if (cond.getPriceMax() != null) {
                predicates.add(cb.le(root.get("price"), cond.getPriceMax()));
            }

            if (cond.getCategoryIds() != null && !cond.getCategoryIds().isEmpty()) {
                predicates.add(root.get("categoryId").in(cond.getCategoryIds()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
