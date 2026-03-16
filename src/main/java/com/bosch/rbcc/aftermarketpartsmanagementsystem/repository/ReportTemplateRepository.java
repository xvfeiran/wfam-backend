package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, String>,
        JpaSpecificationExecutor<ReportTemplate> {

    List<ReportTemplate> findByProductCategoryAndFailureType(String productCategory, String failureType);

    List<ReportTemplate> findByProductCategoryOrFailureType(String productCategory, String failureType);

    List<ReportTemplate> findByEnabled(Integer enabled);

    List<ReportTemplate> findByProductCategoryAndFailureTypeAndEnabled(String productCategory, String failureType, Integer enabled);

    List<ReportTemplate> findByProductCategoryAndEnabled(String productCategory, Integer enabled);
}
