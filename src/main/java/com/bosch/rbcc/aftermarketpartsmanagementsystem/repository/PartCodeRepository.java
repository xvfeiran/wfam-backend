package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.PartCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartCodeRepository extends JpaRepository<PartCode, String>, JpaSpecificationExecutor<PartCode> {
    Optional<PartCode> findByPartCode(String partCode);
    boolean existsByPartCode(String partCode);

    @Query("SELECT DISTINCT p.businessUnit FROM PartCode p WHERE p.businessUnit IS NOT NULL ORDER BY p.businessUnit")
    List<String> findDistinctBusinessUnits();

    @Query("SELECT DISTINCT p.productPlatform FROM PartCode p WHERE p.productPlatform IS NOT NULL ORDER BY p.productPlatform")
    List<String> findDistinctProductPlatforms();
}
