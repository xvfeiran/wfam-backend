package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, String>,
        JpaSpecificationExecutor<Part> {

    List<Part> findByOrderId(String orderId);

    Optional<Part> findByPartNumber(String partNumber);

    @Query(value = "SELECT MAX(TO_NUMBER(SUBSTR(PART_NUMBER, :startPos))) FROM APMS_PART WHERE PART_NUMBER LIKE :pattern", nativeQuery = true)
    Optional<Integer> findMaxSeqByPrefix(@Param("startPos") int startPos, @Param("pattern") String pattern);

    long countByOrderIdAndIsSample(String orderId, int isSample);

    long countByOrderId(String orderId);

    @Query(value = "SELECT * FROM APMS_PART p WHERE p.ORDER_ID IN (SELECT r.ID FROM APMS_RETURN_ORDER r WHERE r.ORDER_NUMBER LIKE :orderNumber)", nativeQuery = true)
    List<Part> findByOrderNumber(@Param("orderNumber") String orderNumber);
}
