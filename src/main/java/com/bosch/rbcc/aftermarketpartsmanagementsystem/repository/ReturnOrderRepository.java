package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReturnOrderRepository extends JpaRepository<ReturnOrder, String>,
        JpaSpecificationExecutor<ReturnOrder> {

    Optional<ReturnOrder> findByOrderNumber(String orderNumber);

    @Query(value = "SELECT MAX(TO_NUMBER(SUBSTR(ORDER_NUMBER, :startPos))) FROM APMS_RETURN_ORDER WHERE ORDER_NUMBER LIKE :pattern", nativeQuery = true)
    Optional<Integer> findMaxSeqByPrefix(@Param("startPos") int startPos, @Param("pattern") String pattern);
}
