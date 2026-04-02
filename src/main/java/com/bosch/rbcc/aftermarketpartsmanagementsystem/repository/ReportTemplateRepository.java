package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, String>,
        JpaSpecificationExecutor<ReportTemplate> {

    List<ReportTemplate> findByProductPlatformAndFailureType(String productPlatform, String failureType);

    List<ReportTemplate> findByProductPlatformOrFailureType(String productPlatform, String failureType);

    List<ReportTemplate> findByEnabled(Integer enabled);

    List<ReportTemplate> findByProductPlatformAndFailureTypeAndEnabled(String productPlatform, String failureType, Integer enabled);

    List<ReportTemplate> findByProductPlatformAndEnabled(String productPlatform, Integer enabled);
}
