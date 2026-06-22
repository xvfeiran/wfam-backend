package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ImportLogDetail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

/**
 * 导入日志明细仓库
 *
 * <p>不作为 Spring Data REST 资源对外暴露（exported = false）。
 * 该仓库仅用于内部 JPA 查询；其中存在同名重载的 finder 方法
 * （{@code findByImportIdOrderByRowNumberAsc}），若被 SDR 暴露会因
 * 映射到同一条 search 路径而导致 ambiguous mapping，并在 springdoc
 * 生成 OpenAPI 时抛出 IllegalStateException。关闭导出可消除该冲突。</p>
 */
@RepositoryRestResource(exported = false)
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
