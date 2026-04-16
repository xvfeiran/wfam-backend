package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ImportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, String>, JpaSpecificationExecutor<ImportRecord> {

    List<ImportRecord> findByStatusAndCreatedAtBefore(String status, LocalDateTime createdAt);

    @Modifying
    @Query(value = "UPDATE APMS_IMPORT_RECORD SET CREATED_AT = :createdAt WHERE ID = :id", nativeQuery = true)
    int touchCreatedAt(@Param("id") String id, @Param("createdAt") LocalDateTime createdAt);

    /**
     * 轻量级进度更新方法 - 仅更新计数器字段，不触碰CLOB
     * 用于导入过程中的进度追踪，避免频繁更新CLOB字段导致性能问题
     * flushAutomatically = true 确保更新前刷新所有挂起的更改
     * clearAutomatically = true 确保更新后清除 JPA 缓存，让下次查询能读到最新数据
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ImportRecord r SET r.totalCount = :total, r.successCount = :success, r.failCount = :fail, r.createdAt = :heartbeat WHERE r.id = :id")
    void updateProgressCounters(@Param("id") String id, @Param("total") int total,
                               @Param("success") int success, @Param("fail") int fail,
                               @Param("heartbeat") LocalDateTime heartbeat);

	@Query("""
		select r.id as id,
			   r.importType as importType,
			   r.fileName as fileName,
			   r.status as status,
			   r.totalCount as totalCount,
			   r.successCount as successCount,
			   r.failCount as failCount,
			   r.createdBy as createdBy,
			   r.createdAt as createdAt
		  from ImportRecord r
		 where (:type is null or :type = '' or r.importType = :type)
		""")
	Page<ImportRecordListView> findListViewByType(@Param("type") String type, Pageable pageable);
}
