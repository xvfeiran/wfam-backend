package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * 分页查询，支持可选的角色限制（loginNameRestriction 非 null 时只返回该分析师的记录）、
     * 分析师筛选和退货单号模糊搜索。
     */
    @Query(value = """
        SELECT new com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO(
            a.id, a.orderId, a.analyst, a.status, a.workonScrapNo, a.scrapStartedAt, a.statusChangedAt,
            a.createdBy, a.createdAt, a.updatedBy, a.updatedAt,
            r.orderNumber
        )
        FROM AnalysisOrder a
        LEFT JOIN ReturnOrder r ON a.orderId = r.id
        WHERE (:loginNameRestriction IS NULL OR a.analyst = :loginNameRestriction)
          AND (:analystFilter IS NULL OR a.analyst = :analystFilter)
          AND (:orderNumberFilter IS NULL OR LOWER(r.orderNumber) LIKE LOWER(CONCAT('%', CONCAT(:orderNumberFilter, '%'))))
        """,
        countQuery = """
        SELECT COUNT(a.id)
        FROM AnalysisOrder a
        LEFT JOIN ReturnOrder r ON a.orderId = r.id
        WHERE (:loginNameRestriction IS NULL OR a.analyst = :loginNameRestriction)
          AND (:analystFilter IS NULL OR a.analyst = :analystFilter)
          AND (:orderNumberFilter IS NULL OR LOWER(r.orderNumber) LIKE LOWER(CONCAT('%', CONCAT(:orderNumberFilter, '%'))))
        """)
    Page<AnalysisOrderWithOrderNumberDTO> findWithFilters(
            @Param("loginNameRestriction") String loginNameRestriction,
            @Param("analystFilter") String analystFilter,
            @Param("orderNumberFilter") String orderNumberFilter,
            Pageable pageable);

    /**
     * 同上，附加状态列表筛选。
     */
    @Query(value = """
        SELECT new com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO(
            a.id, a.orderId, a.analyst, a.status, a.workonScrapNo, a.scrapStartedAt, a.statusChangedAt,
            a.createdBy, a.createdAt, a.updatedBy, a.updatedAt,
            r.orderNumber
        )
        FROM AnalysisOrder a
        LEFT JOIN ReturnOrder r ON a.orderId = r.id
        WHERE (:loginNameRestriction IS NULL OR a.analyst = :loginNameRestriction)
          AND (:analystFilter IS NULL OR a.analyst = :analystFilter)
          AND (:orderNumberFilter IS NULL OR LOWER(r.orderNumber) LIKE LOWER(CONCAT('%', CONCAT(:orderNumberFilter, '%'))))
          AND a.status IN :statuses
        """,
        countQuery = """
        SELECT COUNT(a.id)
        FROM AnalysisOrder a
        LEFT JOIN ReturnOrder r ON a.orderId = r.id
        WHERE (:loginNameRestriction IS NULL OR a.analyst = :loginNameRestriction)
          AND (:analystFilter IS NULL OR a.analyst = :analystFilter)
          AND (:orderNumberFilter IS NULL OR LOWER(r.orderNumber) LIKE LOWER(CONCAT('%', CONCAT(:orderNumberFilter, '%'))))
          AND a.status IN :statuses
        """)
    Page<AnalysisOrderWithOrderNumberDTO> findWithFiltersAndStatuses(
            @Param("loginNameRestriction") String loginNameRestriction,
            @Param("analystFilter") String analystFilter,
            @Param("orderNumberFilter") String orderNumberFilter,
            @Param("statuses") List<String> statuses,
            Pageable pageable);

    long countByStatusAndAnalyst(String status, String analyst);
}
