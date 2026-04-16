package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ImportLogDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 导入日志明细仓库
 */
public interface ImportLogDetailRepository extends JpaRepository<ImportLogDetail, String> {

    /**
     * 根据导入记录ID查询所有日志明细
     */
    List<ImportLogDetail> findByImportIdOrderByRowNumberAsc(String importId);

    /**
     * 根据导入记录ID和状态查询日志明细
     */
    List<ImportLogDetail> findByImportIdAndStatusOrderByRowNumberAsc(String importId, String status);

    /**
     * 根据导入记录ID分页查询日志明细
     */
    Page<ImportLogDetail> findByImportIdOrderByRowNumberAsc(String importId, Pageable pageable);

    /**
     * 根据导入记录ID和文件名分页查询日志明细
     */
    Page<ImportLogDetail> findByImportIdAndFileNameOrderByRowNumberAsc(String importId, String fileName, Pageable pageable);

    /**
     * 删除指定导入记录的所有日志明细
     */
    void deleteByImportId(String importId);

    /**
     * 统计指定导入记录的成功日志数量
     */
    long countByImportIdAndStatus(String importId, String status);
}
