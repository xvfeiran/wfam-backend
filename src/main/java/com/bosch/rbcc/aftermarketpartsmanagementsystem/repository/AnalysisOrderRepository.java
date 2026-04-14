package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisOrderRepository extends JpaRepository<AnalysisOrder, String> {

    Optional<AnalysisOrder> findByOrderIdAndAnalyst(String orderId, String analyst);

    List<AnalysisOrder> findByOrderId(String orderId);

    List<AnalysisOrder> findByAnalyst(String analyst);

    long countByStatus(String status);

    void deleteByOrderIdIn(List<String> orderIds);
}
