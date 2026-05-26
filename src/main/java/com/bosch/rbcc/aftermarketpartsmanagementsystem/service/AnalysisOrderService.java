package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.constant.ComplaintTypeConstants;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisOrderService {

    private static final String STATUS_PENDING_SAMPLING = "pending_sampling";
    private static final String STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";
    private static final String STATUS_ANALYSIS_COMPLETED = "analysis_completed";
    private static final String STATUS_ANALYSIS_SKIPPED = "analysis_skipped";
    private static final String STATUS_WORKON_SCRAP_IN_PROGRESS = "workon_scrap_in_progress";
    private static final String STATUS_WORKON_SCRAPPED = "workon_scrapped";
    private static final String STATUS_SCRAP_IN_PROGRESS = "scrap_in_progress";
    private static final String STATUS_SCRAPPED = "scrapped";

    private final AnalysisOrderRepository analysisOrderRepo;
    private final PartRepository partRepo;
    private final ReturnOrderRepository returnOrderRepo;
    private final ReturnOrderService returnOrderService;

    /**
     * 幂等创建分析单：若已存在则返回现有记录，否则创建新记录。
     * <ul>
     *   <li>0km 退货单：初始状态为 {@code analysis_completed}，表示无需精分析，可直接报废。</li>
     *   <li>售后件退货单：初始状态为 {@code pending_sampling}，走正常抽样 → 精分析 → 报废流程。</li>
     * </ul>
     */
    @Transactional
    public AnalysisOrderDTO getOrCreate(String orderId, String analyst) {
        return analysisOrderRepo.findByOrderIdAndAnalyst(orderId, analyst)
                .map(this::toDTO)
                .orElseGet(() -> {
                    String complaintType = returnOrderRepo.findById(orderId)
                            .map(ReturnOrder::getComplaintType)
                            .orElse(null);
                    String initialStatus = ComplaintTypeConstants.isZeroKm(complaintType)
                            ? STATUS_ANALYSIS_COMPLETED
                            : STATUS_PENDING_SAMPLING;

                    AnalysisOrder ao = AnalysisOrder.builder()
                            .id(UUID.randomUUID().toString())
                            .orderId(orderId)
                            .analyst(analyst)
                            .status(initialStatus)
                            .statusChangedAt(LocalDateTime.now())
                            .build();
                    analysisOrderRepo.save(ao);
                    return toDTO(ao);
                });
    }

    public Page<AnalysisOrderDTO> list(String loginName, String roleNamesStr,
                                       String orderNumber, String analystFilter,
                                       List<String> statuses, Pageable pageable) {
        boolean isAnalyst = roleNamesStr != null
                && roleNamesStr.contains("W_RBCC_AEP_WFAM_Analyst")
                && !roleNamesStr.contains("W_RBCC_AEP_WFAM_QMC_Leader")
                && !roleNamesStr.contains("W_RBCC_AEP_WFAM_QMC_Manager")
                && !roleNamesStr.contains("W_RBCC_AEP_WFAM_SystemAdmin");

        // 角色限制：分析师只能看自己的单，其他角色传 null（不限制）
        String loginNameRestriction = isAnalyst ? loginName : null;
        String orderNumberFilter = (orderNumber != null && !orderNumber.isBlank()) ? orderNumber.trim() : null;
        String analystFilterNorm = (analystFilter != null && !analystFilter.isBlank()) ? analystFilter.trim() : null;

        Page<AnalysisOrderWithOrderNumberDTO> page;
        if (statuses == null || statuses.isEmpty()) {
            page = analysisOrderRepo.findWithFilters(loginNameRestriction, analystFilterNorm, orderNumberFilter, pageable);
        } else {
            page = analysisOrderRepo.findWithFiltersAndStatuses(loginNameRestriction, analystFilterNorm, orderNumberFilter, statuses, pageable);
        }
        return page.map(this::toDTOFromProjection);
    }

    public AnalysisOrderDTO getById(String id) {
        AnalysisOrder ao = analysisOrderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis order not found: " + id));
        return toDTOWithParts(ao);
    }

    @Transactional
    public AnalysisOrderDTO sampling(String id, List<String> sampledPartIds) {
        AnalysisOrder ao = analysisOrderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis order not found: " + id));

        if (!STATUS_PENDING_SAMPLING.equals(ao.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Analysis order must be in pending_sampling status for sampling");
        }

        // 0KM 退货单不允许抽样，只能直接报废
        String complaintType = returnOrderRepo.findById(ao.getOrderId())
                .map(ReturnOrder::getComplaintType)
                .orElse(null);
        if (ComplaintTypeConstants.isZeroKm(complaintType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "0KM退货单不能抽样，只能走报废流程");
        }

        List<Part> parts = partRepo.findByOrderIdAndAnalyst(ao.getOrderId(), ao.getAnalyst());

        for (Part part : parts) {
            boolean sampled = sampledPartIds != null && sampledPartIds.contains(part.getId());
            part.setIsSample(sampled ? 1 : 0);
            if (sampled) {
                part.setStatus(STATUS_IN_DETAILED_ANALYSIS);
            } else {
                part.setStatus(STATUS_ANALYSIS_SKIPPED);
            }
            part.setStatusChangedAt(LocalDateTime.now());
            partRepo.save(part);
        }

        ao.setStatus(STATUS_IN_DETAILED_ANALYSIS);
        ao.setStatusChangedAt(LocalDateTime.now());
        analysisOrderRepo.save(ao);

        return toDTO(ao);
    }

    @Transactional
    public AnalysisOrderDTO scrap(String id) {
        AnalysisOrder ao = analysisOrderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis order not found: " + id));

        if (STATUS_WORKON_SCRAP_IN_PROGRESS.equals(ao.getStatus()) || STATUS_WORKON_SCRAPPED.equals(ao.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Analysis order is already in scrap process");
        }

        // 校验所有售后件是否满足报废条件：未抽样 或 已抽样且精分析审批完成
        List<Part> parts = partRepo.findByOrderIdAndAnalyst(ao.getOrderId(), ao.getAnalyst());
        List<String> unqualifiedParts = parts.stream()
                .filter(p -> !(p.getIsSample() == 0 || STATUS_ANALYSIS_COMPLETED.equals(p.getStatus())))
                .map(p -> p.getPartNumber() != null ? p.getPartNumber() : p.getId())
                .collect(Collectors.toList());
        if (!unqualifiedParts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "以下售后件不满足报废条件（需未抽样或已抽样且精分析审批完成）：" + String.join(", ", unqualifiedParts));
        }

        ao.setStatus(STATUS_WORKON_SCRAP_IN_PROGRESS);
        ao.setStatusChangedAt(LocalDateTime.now());
        analysisOrderRepo.save(ao);

        // 联动更新所有关联 Part 状态
        for (Part part : parts) {
            part.setStatus(STATUS_SCRAP_IN_PROGRESS);
            part.setStatusChangedAt(LocalDateTime.now());
            partRepo.save(part);
        }

        return toDTO(ao);
    }

    @Transactional
    public AnalysisOrderDTO workonConfirm(String id) {
        AnalysisOrder ao = analysisOrderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis order not found: " + id));

        if (!STATUS_WORKON_SCRAP_IN_PROGRESS.equals(ao.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Analysis order must be in workon_scrap_in_progress status");
        }

        ao.setStatus(STATUS_WORKON_SCRAPPED);
        ao.setStatusChangedAt(LocalDateTime.now());
        analysisOrderRepo.save(ao);

        // 联动更新所有关联 Part 状态
        List<Part> parts = partRepo.findByOrderIdAndAnalyst(ao.getOrderId(), ao.getAnalyst());
        for (Part part : parts) {
            part.setStatus(STATUS_SCRAPPED);
            part.setStatusChangedAt(LocalDateTime.now());
            partRepo.save(part);
        }

        // Check if all analysis orders for this return order are scrapped
        returnOrderService.checkAndUpdateToScrappedIfAllScrapped(ao.getOrderId());

        return toDTO(ao);
    }

    private AnalysisOrderDTO toDTO(AnalysisOrder ao) {
        String orderNumber = returnOrderRepo.findById(ao.getOrderId())
                .map(o -> o.getOrderNumber())
                .orElse(null);

        return AnalysisOrderDTO.builder()
                .id(ao.getId())
                .orderId(ao.getOrderId())
                .orderNumber(orderNumber)
                .analyst(ao.getAnalyst())
                .status(ao.getStatus())
                .statusChangedAt(ao.getStatusChangedAt() != null ? ao.getStatusChangedAt().toString() : null)
                .createdBy(ao.getCreatedBy())
                .createdAt(ao.getCreatedAt() != null ? ao.getCreatedAt().toString() : null)
                .updatedBy(ao.getUpdatedBy())
                .updatedAt(ao.getUpdatedAt() != null ? ao.getUpdatedAt().toString() : null)
                .build();
    }

    /**
     * Convert from projection (already JOINED with order number) to DTO.
     * Used by optimized list() method to eliminate N+1 query problem.
     */
    private AnalysisOrderDTO toDTOFromProjection(com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderWithOrderNumberDTO p) {
        return AnalysisOrderDTO.builder()
                .id(p.getId())
                .orderId(p.getOrderId())
                .orderNumber(p.getOrderNumber())
                .analyst(p.getAnalyst())
                .status(p.getStatus())
                .statusChangedAt(p.getStatusChangedAt() != null ? p.getStatusChangedAt().toString() : null)
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null)
                .updatedBy(p.getUpdatedBy())
                .updatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null)
                .build();
    }

    private AnalysisOrderDTO toDTOWithParts(AnalysisOrder ao) {
        AnalysisOrderDTO dto = toDTO(ao);
        List<Part> parts = partRepo.findByOrderIdAndAnalyst(ao.getOrderId(), ao.getAnalyst());
        List<PartDTO> partDTOs = parts.stream().map(p -> PartDTO.builder()
                .id(p.getId())
                .partNumber(p.getPartNumber())
                .orderId(p.getOrderId())
                .partCode(p.getPartCode())
                .businessUnit(p.getBusinessUnit())
                .productPlatform(p.getProductPlatform())
                .partProductionDate(p.getPartProductionDate() != null ? p.getPartProductionDate().toString() : null)
                .productionShift(p.getProductionShift())
                .failureType(p.getFailureType())
                .boschFailureType(p.getBoschFailureType())
                .analyst(p.getAnalyst())
                .isSample(p.getIsSample())
                .status(p.getStatus())
                .images(Collections.emptyList())
                .build()
        ).collect(Collectors.toList());
        dto.setParts(partDTOs);
        return dto;
    }

    public int countByReturnOrderId(String orderId) {
        return (int) analysisOrderRepo.countByOrderId(orderId);
    }

    public int countScrappedByReturnOrderId(String orderId) {
        return (int) analysisOrderRepo.countByOrderIdAndStatus(orderId, STATUS_WORKON_SCRAPPED);
    }

}

