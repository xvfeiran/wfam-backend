package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, String>,
        JpaSpecificationExecutor<AnalysisReport> {

    Optional<AnalysisReport> findByPartId(String partId);

    List<AnalysisReport> findByStatus(String status);

    List<AnalysisReport> findByTemplateId(String templateId);

    List<AnalysisReport> findByPartIdAndStatus(String partId, String status);
}
