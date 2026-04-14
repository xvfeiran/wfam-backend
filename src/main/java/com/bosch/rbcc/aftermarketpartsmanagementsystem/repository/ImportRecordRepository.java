package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ImportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, String>, JpaSpecificationExecutor<ImportRecord> {

    List<ImportRecord> findByStatusAndCreatedAtBefore(String status, LocalDateTime createdAt);

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
