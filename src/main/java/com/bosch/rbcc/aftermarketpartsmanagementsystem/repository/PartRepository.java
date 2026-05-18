package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, String>,
        JpaSpecificationExecutor<Part> {

    List<Part> findByOrderId(String orderId);

    Optional<Part> findByPartNumber(String partNumber);

    boolean existsByOrderIdAndPartNumber(String orderId, String partNumber);

    boolean existsByOrderIdAndPartNumberAndIdNot(String orderId, String partNumber, String id);

    @Query(value = "SELECT MAX(TO_NUMBER(SUBSTR(PART_NUMBER, :startPos))) FROM APMS_PART WHERE PART_NUMBER LIKE :pattern AND ORDER_ID = :orderId", nativeQuery = true)
    Optional<Integer> findMaxSeqByPrefixAndOrderId(@Param("startPos") int startPos, @Param("pattern") String pattern, @Param("orderId") String orderId);

    long countByOrderIdAndIsSample(String orderId, int isSample);

    long countByOrderId(String orderId);

    @Query(value = "SELECT * FROM APMS_PART p WHERE p.ORDER_ID IN (SELECT r.ID FROM APMS_RETURN_ORDER r WHERE r.ORDER_NUMBER LIKE :orderNumber)", nativeQuery = true)
    List<Part> findByOrderNumber(@Param("orderNumber") String orderNumber);

    List<Part> findByOrderIdAndAnalyst(String orderId, String analyst);

    long countByStatus(String status);

    long countByStatusIn(Collection<String> statuses);

    long countByStatusAndStatusChangedAtLessThanEqual(String status, LocalDateTime statusChangedAt);

    List<Part> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query(value = "SELECT TRUNC(CREATED_AT) AS day, COUNT(*) AS cnt FROM APMS_PART WHERE TRUNC(CREATED_AT) BETWEEN :startDate AND :endDate GROUP BY TRUNC(CREATED_AT)", nativeQuery = true)
    List<Object[]> countDailyByCreatedDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT DISTINCT TRIM(p.failureType) FROM Part p WHERE p.failureType IS NOT NULL AND TRIM(p.failureType) <> '' ORDER BY TRIM(p.failureType)")
    List<String> findDistinctFailureTypes();

    @Modifying
    @Query(value = "UPDATE APMS_PART SET CREATED_AT = :createdAt WHERE ID = :id", nativeQuery = true)
    int updateCreatedAt(@Param("id") String id, @Param("createdAt") LocalDateTime createdAt);

    long countByStatusAndAnalyst(String status, String analyst);

    long countByStatusInAndAnalyst(Collection<String> statuses, String analyst);

    long countByStatusAndAnalystAndStatusChangedAtLessThanEqual(String status, String analyst, LocalDateTime statusChangedAt);
}
