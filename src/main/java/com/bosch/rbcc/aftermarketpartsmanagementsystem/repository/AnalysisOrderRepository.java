package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnalysisOrderRepository extends JpaRepository<AnalysisOrder, String> {

    Optional<AnalysisOrder> findByOrderIdAndAnalyst(String orderId, String analyst);

    List<AnalysisOrder> findByOrderId(String orderId);

    List<AnalysisOrder> findByAnalyst(String analyst);

    long countByStatus(String status);

    /**
     * Count total analysis orders for a return order
     */
    long countByOrderId(String orderId);

    /**
     * Count analysis orders with workon_scrapped status for a return order
     */
    long countByOrderIdAndStatus(String orderId, String status);

    void deleteByOrderIdIn(List<String> orderIds);

    /**
     * Fetch all analysis orders with their return order numbers in one query.
     * Uses LEFT JOIN to handle cases where return order might be deleted.
     */
    @Query("""
        SELECT new com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO(
            a.id, a.orderId, a.analyst, a.status, a.statusChangedAt,
            a.createdBy, a.createdAt, a.updatedBy, a.updatedAt,
            r.orderNumber
        )
        FROM AnalysisOrder a
        LEFT JOIN ReturnOrder r ON a.orderId = r.id
        """)
    List<AnalysisOrderWithOrderNumberDTO> findAllWithOrderNumbers();

    /**
     * Fetch analysis orders by analyst with their return order numbers in one query.
     */
    @Query("""
        SELECT new com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO(
            a.id, a.orderId, a.analyst, a.status, a.statusChangedAt,
            a.createdBy, a.createdAt, a.updatedBy, a.updatedAt,
            r.orderNumber
        )
        FROM AnalysisOrder a
        LEFT JOIN ReturnOrder r ON a.orderId = r.id
        WHERE a.analyst = :analyst
        """)
    List<AnalysisOrderWithOrderNumberDTO> findByAnalystWithOrderNumbers(String analyst);

    /**
     * Fetch analysis orders by status list with their return order numbers.
     */
    @Query("""
        SELECT new com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO(
            a.id, a.orderId, a.analyst, a.status, a.statusChangedAt,
            a.createdBy, a.createdAt, a.updatedBy, a.updatedAt,
            r.orderNumber
        )
        FROM AnalysisOrder a
        LEFT JOIN ReturnOrder r ON a.orderId = r.id
        WHERE a.status IN :statuses
        """)
    List<AnalysisOrderWithOrderNumberDTO> findByStatusIn(List<String> statuses);

    /**
     * Fetch analysis orders by analyst and status list with their return order numbers.
     */
    @Query("""
        SELECT new com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO(
            a.id, a.orderId, a.analyst, a.status, a.statusChangedAt,
            a.createdBy, a.createdAt, a.updatedBy, a.updatedAt,
            r.orderNumber
        )
        FROM AnalysisOrder a
        LEFT JOIN ReturnOrder r ON a.orderId = r.id
        WHERE a.analyst = :analyst AND a.status IN :statuses
        """)
    List<AnalysisOrderWithOrderNumberDTO> findByAnalystAndStatusIn(String analyst, List<String> statuses);

    long countByStatusAndAnalyst(String status, String analyst);
}
