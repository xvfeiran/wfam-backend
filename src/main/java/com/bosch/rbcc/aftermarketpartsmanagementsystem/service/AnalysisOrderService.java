package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
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
    private static final String STATUS_WORKON_SCRAP_IN_PROGRESS = "workon_scrap_in_progress";
    private static final String STATUS_WORKON_SCRAPPED = "workon_scrapped";
    private static final String STATUS_SCRAP_IN_PROGRESS = "scrap_in_progress";

    private final AnalysisOrderRepository analysisOrderRepo;
    private final PartRepository partRepo;
    private final ReturnOrderRepository returnOrderRepo;

    /**
     * 幂等创建分析单：若已存在则返回现有记录，否则创建新记录。
     */
    @Transactional
    public AnalysisOrderDTO getOrCreate(String orderId, String analyst) {
        return analysisOrderRepo.findByOrderIdAndAnalyst(orderId, analyst)
                .map(this::toDTO)
                .orElseGet(() -> {
                    AnalysisOrder ao = AnalysisOrder.builder()
                            .id(UUID.randomUUID().toString())
                            .orderId(orderId)
                            .analyst(analyst)
                            .status(STATUS_PENDING_SAMPLING)
                            .statusChangedAt(LocalDateTime.now())
                            .build();
                    analysisOrderRepo.save(ao);
                    return toDTO(ao);
                });
    }

    public List<AnalysisOrderDTO> list(String loginName, String roleNamesStr) {
        List<AnalysisOrder> orders;
        boolean isAnalyst = roleNamesStr != null
                && roleNamesStr.contains("W_RBCC_AEP_WFAM_Analyst")
                && !roleNamesStr.contains("W_RBCC_AEP_WFAM_QMC_Leader")
                && !roleNamesStr.contains("W_RBCC_AEP_WFAM_QMC_Manager")
                && !roleNamesStr.contains("W_RBCC_AEP_WFAM_SystemAdmin");

        if (isAnalyst) {
            orders = analysisOrderRepo.findByAnalyst(loginName);
        } else {
            orders = analysisOrderRepo.findAll();
        }
        return orders.stream().map(this::toDTO).collect(Collectors.toList());
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

        List<Part> parts = partRepo.findByOrderIdAndAnalyst(ao.getOrderId(), ao.getAnalyst());

        for (Part part : parts) {
            boolean sampled = sampledPartIds != null && sampledPartIds.contains(part.getId());
            part.setIsSample(sampled ? 1 : 0);
            if (sampled) {
                part.setStatus(STATUS_IN_DETAILED_ANALYSIS);
                part.setStatusChangedAt(LocalDateTime.now());
            }
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

        ao.setStatus(STATUS_WORKON_SCRAP_IN_PROGRESS);
        ao.setStatusChangedAt(LocalDateTime.now());
        analysisOrderRepo.save(ao);

        // 联动更新所有关联 Part 状态
        List<Part> parts = partRepo.findByOrderIdAndAnalyst(ao.getOrderId(), ao.getAnalyst());
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

    private AnalysisOrderDTO toDTOWithParts(AnalysisOrder ao) {
        AnalysisOrderDTO dto = toDTO(ao);
        List<Part> parts = partRepo.findByOrderIdAndAnalyst(ao.getOrderId(), ao.getAnalyst());
        List<PartDTO> partDTOs = parts.stream().map(p -> PartDTO.builder()
                .id(p.getId())
                .partNumber(p.getPartNumber())
                .orderId(p.getOrderId())
                .partCode(p.getPartCode())
                .businessUnit(p.getBusinessUnit())
                .productCategory(p.getProductCategory())
                .productPlatform(p.getProductPlatform())
                .productionShift(p.getProductionShift())
                .failureType(p.getFailureType())
                .boschFailureType(p.getBoschFailureType())
                .analyst(p.getAnalyst())
                .isSample(p.getIsSample())
                .status(p.getStatus())
                .images(List.of())
                .build()
        ).collect(Collectors.toList());
        dto.setParts(partDTOs);
        return dto;
    }

}

