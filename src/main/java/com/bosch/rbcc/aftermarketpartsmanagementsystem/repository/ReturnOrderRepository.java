package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReturnOrderRepository extends JpaRepository<ReturnOrder, String>,
        JpaSpecificationExecutor<ReturnOrder> {

    Optional<ReturnOrder> findByOrderNumber(String orderNumber);

    @Query(value = "SELECT MAX(TO_NUMBER(SUBSTR(ORDER_NUMBER, :startPos))) FROM APMS_RETURN_ORDER WHERE ORDER_NUMBER LIKE :pattern", nativeQuery = true)
    Optional<Integer> findMaxSeqByPrefix(@Param("startPos") int startPos, @Param("pattern") String pattern);

    List<ReturnOrder> findByReceiveDateBetween(LocalDate start, LocalDate end);

    @Query(value = "SELECT RECEIVE_DATE AS day, COUNT(*) AS cnt FROM APMS_RETURN_ORDER WHERE RECEIVE_DATE BETWEEN :startDate AND :endDate GROUP BY RECEIVE_DATE", nativeQuery = true)
    List<Object[]> countDailyByReceiveDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT DISTINCT TRIM(r.complaintType) FROM ReturnOrder r WHERE r.complaintType IS NOT NULL AND TRIM(r.complaintType) <> '' ORDER BY TRIM(r.complaintType)")
    List<String> findDistinctComplaintTypes();

    @Modifying
    @Query(value = "UPDATE APMS_RETURN_ORDER SET CREATED_AT = :createdAt WHERE ID = :id", nativeQuery = true)
    int updateCreatedAt(@Param("id") String id, @Param("createdAt") LocalDateTime createdAt);
}
