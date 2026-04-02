package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.PartCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartCodeRepository extends JpaRepository<PartCode, String>, JpaSpecificationExecutor<PartCode> {
    Optional<PartCode> findByPartCode(String partCode);
    boolean existsByPartCode(String partCode);
}
