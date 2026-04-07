package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartService {

    private static final String ROLE_QMC_LEADER = "W_RBCC_AEP_WFAM_QMC_Manager";

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SUBMITTED = "submitted";
    private static final String STATUS_IN_INITIAL_ANALYSIS = "in_initial_analysis";
    private static final String STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";
    private static final String STATUS_ANALYSIS_COMPLETED = "analysis_completed";
    private static final String STATUS_SCRAP_IN_PROGRESS = "scrap_in_progress";
    private static final String STATUS_SCRAPPED = "scrapped";
    private static final Set<String> QC_ALLOWED_STATUSES = Set.of(
            STATUS_PENDING_APPROVAL, STATUS_ANALYSIS_COMPLETED, STATUS_SCRAP_IN_PROGRESS, STATUS_SCRAPPED);

    private final PartRepository partRepo;
    private final ReturnOrderRepository returnOrderRepository;
    private final AnalysisOrderService analysisOrderService;

    public Page<PartDTO> list(String orderNumber, String partCode, String businessUnit,
                              String productPlatform, String status, String qcCreated,
                              String analyst, int page, int size) {
        log.info("[PartService] list() start, page={}, size={}, orderNumber={}, status={}", page, size, orderNumber, status);

        Page<Part> partPage = partRepo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (orderNumber != null && !orderNumber.isBlank()) {
                // 子查询匹配退货单号
                Subquery<String> subquery = query.subquery(String.class);
                Root<ReturnOrder> roRoot = subquery.from(ReturnOrder.class);
                subquery.select(roRoot.get("id"));
                subquery.where(cb.like(cb.upper(roRoot.get("orderNumber")), "%" + orderNumber.toUpperCase() + "%"));
                predicates.add(root.get("orderId").in(subquery));
            }
            if (partCode != null && !partCode.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("partCode")), "%" + partCode.toUpperCase() + "%"));
            }
            if (businessUnit != null && !businessUnit.isBlank()) {
                predicates.add(cb.equal(root.get("businessUnit"), businessUnit));
            }
            if (productPlatform != null && !productPlatform.isBlank()) {
                predicates.add(cb.equal(root.get("productPlatform"), productPlatform));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (analyst != null && !analyst.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("analyst")), "%" + analyst.toLowerCase() + "%"));
            }
            if ("yes".equals(qcCreated)) {
                predicates.add(cb.isNotNull(root.get("qcNo")));
                predicates.add(cb.notEqual(root.get("qcNo"), ""));
            } else if ("no".equals(qcCreated)) {
                predicates.add(cb.or(
                        cb.isNull(root.get("qcNo")),
                        cb.equal(root.get("qcNo"), "")
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, PageRequest.of(page, size));

        log.info("[PartService] list() page {}/{}, {} records this page", page, partPage.getTotalPages(), partPage.getNumberOfElements());

        // 仅对当前页批量查询退货单号
        Set<String> orderIds = partPage.getContent().stream()
                .map(Part::getOrderId).collect(Collectors.toSet());
        Map<String, String> orderIdToNumber = orderIds.isEmpty()
                ? Collections.emptyMap()
                : returnOrderRepository.findAllById(orderIds).stream()
                        .collect(Collectors.toMap(
                                o -> o.getId(),
                                o -> o.getOrderNumber() != null ? o.getOrderNumber() : ""
                        ));

        return partPage.map(p -> buildDTO(p, orderIdToNumber.get(p.getOrderId())));
    }

    public PartDTO getById(String id) {
        return partRepo.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));
    }

    @Transactional
    public PartDTO create(PartDTO dto) {
        // Check if the associated return order allows adding new parts
        var returnOrder = returnOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return order not found: " + dto.getOrderId()));

        String orderStatus = returnOrder.getStatus();
        // Only draft and submitted status can add parts
        if (!STATUS_DRAFT.equals(orderStatus) && !STATUS_SUBMITTED.equals(orderStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Parts can only be added to return orders in 'draft' or 'submitted' status. Current status: " + orderStatus);
        }

        // Analyst is required
        if (dto.getAnalyst() == null || dto.getAnalyst().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analyst is required");
        }

        Part part = Part.builder()
                .id(UUID.randomUUID().toString())
                .orderId(dto.getOrderId())
                .partCode(dto.getPartCode())
                .businessUnit(dto.getBusinessUnit())
                .productPlatform(dto.getProductPlatform())
                .productionShift(dto.getProductionShift())
                .failureType(dto.getFailureType())
                .boschFailureType(dto.getBoschFailureType())
                .vehicleProductionDate(parseDate(dto.getVehicleProductionDate()))
                .vehiclePurchaseDate(parseDate(dto.getVehiclePurchaseDate()))
                .vehicleFailureDate(parseDate(dto.getVehicleFailureDate()))
                .vehicleVin(dto.getVehicleVIN())
                .vehicleMileage(dto.getVehicleMileage())
                .customerDescription(dto.getCustomerDescription())
                .otherDescription(dto.getOtherDescription())
                .repairStation(dto.getRepairStation())
                .complaintLocation(dto.getComplaintLocation())
                .responsibleEngineer(dto.getResponsibleEngineer())
                .analyst(dto.getAnalyst())
                .status(STATUS_IN_INITIAL_ANALYSIS)
                .statusChangedAt(LocalDateTime.now())
                .build();
        partRepo.save(part);

        // 触发分析单自动创建（幂等）
        analysisOrderService.getOrCreate(dto.getOrderId(), dto.getAnalyst());

        return toDTO(part);
    }

    @Transactional
    public PartDTO update(String id, PartDTO dto) {
        Part part = partRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));

        // Check permission: submitted parts (with partNumber) can only be edited by QMC Leader
        if (part.getPartNumber() != null) {
            boolean isQMCLeader = hasRole(ROLE_QMC_LEADER);
            if (!isQMCLeader) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only QMC Leader can edit submitted parts");
            }
        }

        part.setPartCode(dto.getPartCode());
        part.setBusinessUnit(dto.getBusinessUnit());
        part.setProductPlatform(dto.getProductPlatform());
        part.setProductionShift(dto.getProductionShift());
        part.setFailureType(dto.getFailureType());
        part.setBoschFailureType(dto.getBoschFailureType());
        part.setVehicleProductionDate(parseDate(dto.getVehicleProductionDate()));
        part.setVehiclePurchaseDate(parseDate(dto.getVehiclePurchaseDate()));
        part.setVehicleFailureDate(parseDate(dto.getVehicleFailureDate()));
        part.setVehicleVin(dto.getVehicleVIN());
        part.setVehicleMileage(dto.getVehicleMileage());
        part.setCustomerDescription(dto.getCustomerDescription());
        part.setOtherDescription(dto.getOtherDescription());
        part.setRepairStation(dto.getRepairStation());
        part.setComplaintLocation(dto.getComplaintLocation());
        part.setResponsibleEngineer(dto.getResponsibleEngineer());
        part.setAnalyst(dto.getAnalyst());
        partRepo.save(part);
        return toDTO(part);
    }

    @Transactional
    public void delete(String id) {
        Part part = partRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));

        // Check permission: submitted parts (with partNumber) can only be deleted by QMC Leader
        if (part.getPartNumber() != null) {
            boolean isQMCLeader = hasRole(ROLE_QMC_LEADER);
            if (!isQMCLeader) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only QMC Leader can delete submitted parts");
            }
        }

        partRepo.delete(part);
    }

    @Transactional
    public PartDTO submit(String id) {
        Part part = partRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));
        // 如果尚未生成零件编号，则生成新编号
        if (part.getPartNumber() == null) {
            part.setPartNumber(generatePartNumber(part.getBusinessUnit(), part.getProductPlatform()));
        }
        // 已提交的单据也可以再次提交（用于更新数据），只保存更新
        partRepo.save(part);
        return toDTO(part);
    }

    @Transactional
    public PartDTO updateQcNo(String id, String qcNo) {
        Part part = partRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));
        if (!QC_ALLOWED_STATUSES.contains(part.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "QC No. can only be set when status is: " + QC_ALLOWED_STATUSES);
        }
        part.setQcNo(qcNo);
        partRepo.save(part);
        return toDTO(part);
    }

    @Transactional
    public PartDTO updateStatus(String id, String newStatus) {
        Part part = partRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));
        part.setStatus(newStatus);
        part.setStatusChangedAt(LocalDateTime.now());
        partRepo.save(part);
        return toDTO(part);
    }

    private String generatePartNumber(String bu, String productPlatform) {
        String prefix = bu + "-" + productPlatform + "-";
        // startPos: 1-based index after the prefix (e.g. "RBCA-BS-" = 8 chars, seq starts at pos 9)
        int maxSeq = partRepo.findMaxSeqByPrefix(prefix.length() + 1, prefix + "%").orElse(0);
        return prefix + String.format("%04d", maxSeq + 1);
    }

    private boolean hasRole(String roleName) {
        var headers = CommonHeaderManager.getCommonHeaders();
        if (headers == null || headers.getRoleNames() == null) {
            return false;
        }
        return headers.getRoleNames().contains(roleName);
    }

    private PartDTO toDTO(Part part, Map<String, String> orderIdToNumber) {
        String orderNumber = orderIdToNumber.get(part.getOrderId());
        return buildDTO(part, orderNumber);
    }

    private PartDTO toDTO(Part part) {
        // 从退货单表中查询关联的退货单编号（单条查询场景）
        String orderNumber = returnOrderRepository.findById(part.getOrderId())
                .map(order -> order.getOrderNumber())
                .orElse(null);
        return buildDTO(part, orderNumber);
    }

    private PartDTO buildDTO(Part part, String orderNumber) {
        return PartDTO.builder()
                .id(part.getId())
                .partNumber(part.getPartNumber())
                .orderId(part.getOrderId())
                .orderNumber(orderNumber)
                .partCode(part.getPartCode())
                .businessUnit(part.getBusinessUnit())
                .productPlatform(part.getProductPlatform())
                .productionShift(part.getProductionShift())
                .failureType(part.getFailureType())
                .boschFailureType(part.getBoschFailureType())
                .repairStation(part.getRepairStation())
                .complaintLocation(part.getComplaintLocation())
                .responsibleEngineer(part.getResponsibleEngineer())
                .analyst(part.getAnalyst())
                .qcNo(part.getQcNo())
                .vehicleProductionDate(part.getVehicleProductionDate() != null ? part.getVehicleProductionDate().toString() : null)
                .vehiclePurchaseDate(part.getVehiclePurchaseDate() != null ? part.getVehiclePurchaseDate().toString() : null)
                .vehicleFailureDate(part.getVehicleFailureDate() != null ? part.getVehicleFailureDate().toString() : null)
                .vehicleVIN(part.getVehicleVin())
                .vehicleMileage(part.getVehicleMileage())
                .customerDescription(part.getCustomerDescription())
                .otherDescription(part.getOtherDescription())
                .status(part.getStatus())
                .images(List.of())
                .createdBy(part.getCreatedBy())
                .createdAt(part.getCreatedAt() != null ? part.getCreatedAt().toString() : null)
                .updatedBy(part.getUpdatedBy())
                .updatedAt(part.getUpdatedAt() != null ? part.getUpdatedAt().toString() : null)
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
