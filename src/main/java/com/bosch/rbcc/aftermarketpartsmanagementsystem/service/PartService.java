package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.OcrTask;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.OcrTaskRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.ExportProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.constant.ComplaintTypeConstants;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel.PartExcelHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private static final String STATUS_INITIAL_ANALYSIS_COMPLETED = "initial_analysis_completed";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";
    private static final String STATUS_ANALYSIS_COMPLETED = "analysis_completed";
    private static final String STATUS_ANALYSIS_SKIPPED = "analysis_skipped";
    private static final String STATUS_SCRAP_IN_PROGRESS = "scrap_in_progress";
    private static final String STATUS_SCRAPPED = "scrapped";
    private static final Set<String> QC_ALLOWED_STATUSES = Set.of(
            STATUS_PENDING_APPROVAL, STATUS_ANALYSIS_COMPLETED, STATUS_ANALYSIS_SKIPPED,
            STATUS_SCRAP_IN_PROGRESS, STATUS_SCRAPPED);

    private final PartRepository partRepo;
    private final OcrTaskRepository ocrTaskRepo;
    private final ReturnOrderRepository returnOrderRepository;
    private final AnalysisOrderRepository analysisOrderRepo;
    private final OcrService ocrService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final PartExcelHandler partExcelHandler;
    private final ExportProperties exportProperties;


    public Page<PartDTO> list(String orderNumber, String partCode, String businessUnit,
            String productPlatform, String status, String qcCreated,
            String analyst, LocalDate partProductionDateFrom, LocalDate partProductionDateTo,
            Integer vehicleMileageFrom, Integer vehicleMileageTo,
            int page, int size, String sortBy, String sortOrder) {

        Page<Part> partPage = partRepo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (orderNumber != null && !orderNumber.isBlank()) {
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
            } else if ("no".equals(qcCreated)) {
                predicates.add(cb.or(
                        cb.isNull(root.get("qcNo")),
                        cb.equal(root.get("qcNo"), "")));
            }
            if (partProductionDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("partProductionDate"), partProductionDateFrom));
            }
            if (partProductionDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("partProductionDate"), partProductionDateTo));
            }
            if (vehicleMileageFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("vehicleMileage"), vehicleMileageFrom));
            }
            if (vehicleMileageTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("vehicleMileage"), vehicleMileageTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, buildPageRequest(page, size, sortBy, sortOrder));

        // 仅对当前页批量查询退货单号
        Set<String> orderIds = partPage.getContent().stream()
                .map(Part::getOrderId).collect(Collectors.toSet());
        Map<String, String> orderIdToNumber = orderIds.isEmpty()
                ? Collections.emptyMap()
                : returnOrderRepository.findAllById(orderIds).stream()
                        .collect(Collectors.toMap(
                                o -> o.getId(),
                                o -> o.getOrderNumber() != null ? o.getOrderNumber() : ""));

        return partPage.map(p -> buildDTO(p, orderIdToNumber.get(p.getOrderId())));
    }

    public byte[] exportToExcel(String orderNumber, String partCode, String businessUnit,
            String productPlatform, String status, String qcCreated, String analyst,
            LocalDate partProductionDateFrom, LocalDate partProductionDateTo,
            Integer vehicleMileageFrom, Integer vehicleMileageTo) {
        List<Part> matchingParts = partRepo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (orderNumber != null && !orderNumber.isBlank()) {
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
            } else if ("no".equals(qcCreated)) {
                predicates.add(cb.or(cb.isNull(root.get("qcNo")), cb.equal(root.get("qcNo"), "")));
            }
            if (partProductionDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("partProductionDate"), partProductionDateFrom));
            }
            if (partProductionDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("partProductionDate"), partProductionDateTo));
            }
            if (vehicleMileageFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("vehicleMileage"), vehicleMileageFrom));
            }
            if (vehicleMileageTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("vehicleMileage"), vehicleMileageTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });

        int maxRows = exportProperties.getMaxRows();
        if (matchingParts.size() > maxRows) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "导出数据量（" + matchingParts.size() + " 条）超过上限 " + maxRows + " 条，请缩小筛选条件范围");
        }

        // Batch load order numbers
        Set<String> orderIds = matchingParts.stream().map(Part::getOrderId).collect(Collectors.toSet());
        Map<String, String> orderIdToNumber = orderIds.isEmpty()
                ? Collections.emptyMap()
                : returnOrderRepository.findAllById(orderIds).stream()
                        .collect(Collectors.toMap(ReturnOrder::getId, o -> o.getOrderNumber() != null ? o.getOrderNumber() : ""));

        List<PartDTO> dtos = matchingParts.stream()
                .map(p -> buildDTO(p, orderIdToNumber.get(p.getOrderId())))
                .collect(Collectors.toList());

        return partExcelHandler.exportToExcel(dtos);
    }

    private PageRequest buildPageRequest(int page, int size, String sortBy, String sortOrder) {
        Sort.Direction direction = "ascend".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = resolveSortField(sortBy);
        return PageRequest.of(page, size, Sort.by(direction, sortField));
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "updatedAt";
        }

        return switch (sortBy) {
            case "partNumber" -> "partNumber";
            case "partCode" -> "partCode";
            case "businessUnit" -> "businessUnit";
            case "productPlatform" -> "productPlatform";
            case "analyst" -> "analyst";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            // orderNumber is not a Part column; use orderId for deterministic DB-side
            // ordering.
            case "orderNumber" -> "orderId";
            default -> "updatedAt";
        };
    }

    public PartDTO getById(String id) {
        return partRepo.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));
    }

    /**
     * 检查 partNumber 在同一退货单下是否可用（排除指定 partId）。
     */
    @Transactional(readOnly = true)
    public boolean isPartNumberAvailable(String partNumber, String orderId, String excludeId) {
        if (partNumber == null || partNumber.isBlank() || orderId == null || orderId.isBlank()) {
            return false;
        }
        if (excludeId != null && !excludeId.isBlank()) {
            return !partRepo.existsByOrderIdAndPartNumberAndIdNot(orderId, partNumber, excludeId);
        }
        return !partRepo.existsByOrderIdAndPartNumber(orderId, partNumber);
    }

    @Transactional
    public PartDTO create(PartDTO dto, String ocrTaskId) {
        // Check if the associated return order allows adding new parts
        var returnOrder = returnOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Return order not found: " + dto.getOrderId()));

        String orderStatus = returnOrder.getStatus();
        if (!STATUS_DRAFT.equals(orderStatus) && !STATUS_SUBMITTED.equals(orderStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parts can only be added to return orders in 'draft' or 'submitted' status. Current status: "
                            + orderStatus);
        }

        // Block part creation after end-entry (analysis orders exist)
        if (analysisOrderRepo.countByOrderId(dto.getOrderId()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot add parts after entry has been ended");
        }

        // Analyst is required
        if (dto.getAnalyst() == null || dto.getAnalyst().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analyst is required");
        }

        // partNumber 唯一性校验
        if (dto.getPartNumber() != null && !dto.getPartNumber().isBlank()) {
            if (partRepo.existsByOrderIdAndPartNumber(dto.getOrderId(), dto.getPartNumber())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Part number '" + dto.getPartNumber() + "' already exists in this return order");
            }
        }

        Part part = Part.builder()
                .id(UUID.randomUUID().toString())
                .orderId(trimText(dto.getOrderId()))
                .partNumber(trimText(dto.getPartNumber()))
                .partCode(trimText(dto.getPartCode()))
                .businessUnit(trimText(dto.getBusinessUnit()))
                .productPlatform(trimText(dto.getProductPlatform()))
                .partProductionDate(parseDate(dto.getPartProductionDate()))
                .productionShift(trimText(dto.getProductionShift()))
                .failureType(trimText(dto.getFailureType()))
                .boschFailureType(trimText(dto.getBoschFailureType()))
                .vehicleProductionDate(parseDate(dto.getVehicleProductionDate()))
                .vehiclePurchaseDate(parseDate(dto.getVehiclePurchaseDate()))
                .vehicleFailureDate(parseDate(dto.getVehicleFailureDate()))
                .vehicleVin(trimText(dto.getVehicleVIN()))
                .vehicleMileage(dto.getVehicleMileage())
                .customerDescription(trimText(dto.getCustomerDescription()))
                .otherDescription(trimText(dto.getOtherDescription()))
                .otherInfo(trimText(dto.getOtherInfo()))
                .repairStation(trimText(dto.getRepairStation())).complaintLocation(trimText(dto.getComplaintLocation()))
                .responsibleEngineer(trimText(dto.getResponsibleEngineer()))
                .analyst(trimText(dto.getAnalyst()))
                .qcNo(trimText(dto.getQcNo()))
                .status(STATUS_IN_INITIAL_ANALYSIS)
                .statusChangedAt(LocalDateTime.now())
                .build();
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            try {
                part.setImages(objectMapper.writeValueAsString(dto.getImages()));
            } catch (Exception e) {
                log.warn("序列化图片列表失败", e);
            }
        }
        try {
            partRepo.save(part);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Part number '" + dto.getPartNumber() + "' already exists in this return order");
        }

        // 检查是否为 0km 退货，触发通知（returnOrder 在方法头部已加载，直接复用）
        notificationService.sendZeroKmNotification(part.getId(), returnOrder.getComplaintType());

        // 绑定 OCR 任务（新建模式下在此时才有 partId）
        if (ocrTaskId != null && !ocrTaskId.isBlank()) {
            ocrService.bindTaskToPart(ocrTaskId, part.getId());
        }

        return toDTO(part);
    }

    @Transactional
    public PartDTO createForImport(PartDTO dto) {
        var returnOrder = returnOrderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Return order not found: " + dto.getOrderId()));

        LocalDateTime importCreatedAt = parseDateTime(dto.getCreatedAt());

        String orderStatus = returnOrder.getStatus();
        if (!STATUS_DRAFT.equals(orderStatus) && !STATUS_SUBMITTED.equals(orderStatus) && !STATUS_SCRAPPED.equals(orderStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parts can only be imported into return orders in 'draft', 'submitted' or 'scrapped' status. Current status: "
                            + orderStatus);
        }

        if (dto.getAnalyst() == null || dto.getAnalyst().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analyst is required");
        }

        Part part = Part.builder()
                .id(UUID.randomUUID().toString())
                .orderId(trimText(dto.getOrderId()))
                .partCode(trimText(dto.getPartCode()))
                .businessUnit(trimText(dto.getBusinessUnit()))
                .productPlatform(trimText(dto.getProductPlatform()))
                .partNumber(generatePartNumber(dto.getBusinessUnit(), dto.getProductPlatform(), dto.getOrderId()))
                .partProductionDate(parseDate(dto.getPartProductionDate()))
                .productionShift(trimText(dto.getProductionShift()))
                .failureType(trimText(dto.getFailureType()))
                .boschFailureType(trimText(dto.getBoschFailureType()))
                .vehicleProductionDate(parseDate(dto.getVehicleProductionDate()))
                .vehiclePurchaseDate(parseDate(dto.getVehiclePurchaseDate()))
                .vehicleFailureDate(parseDate(dto.getVehicleFailureDate()))
                .vehicleVin(trimText(dto.getVehicleVIN()))
                .vehicleMileage(dto.getVehicleMileage())
                .customerDescription(trimText(dto.getCustomerDescription()))
                .otherDescription(trimText(dto.getOtherDescription()))
                .otherInfo(trimText(dto.getOtherInfo()))
                .repairStation(trimText(dto.getRepairStation())).complaintLocation(trimText(dto.getComplaintLocation()))
                .responsibleEngineer(trimText(dto.getResponsibleEngineer()))
                .analyst(trimText(dto.getAnalyst()))
                .qcNo(trimText(dto.getQcNo()))
                .status(STATUS_SCRAPPED)
                .statusChangedAt(LocalDateTime.now())
                .build();
        partRepo.save(part);

        if (importCreatedAt != null) {
            partRepo.updateCreatedAt(part.getId(), importCreatedAt);
            part.setCreatedAt(importCreatedAt);
        }

        return toDTO(part);
    }

    /**
     * 批量导入专用：批量创建售后件。
     * 使用 Hibernate 批量插入优化性能，每批最多200条。
     *
     * @param dtos 售后件DTO列表
     * @return 成功创建的售后件DTO列表
     */
    @Transactional
    public List<PartDTO> createForImportBatch(List<PartDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }

        // 验证所有退货单存在且状态正确
        Set<String> orderIds = dtos.stream()
                .map(PartDTO::getOrderId)
                .collect(Collectors.toSet());

        Map<String, ReturnOrder> ordersMap = returnOrderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(ReturnOrder::getId, o -> o));

        // 检查退货单状态和analyst
        for (PartDTO dto : dtos) {
            ReturnOrder returnOrder = ordersMap.get(dto.getOrderId());
            if (returnOrder == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Return order not found: " + dto.getOrderId());
            }

            String orderStatus = returnOrder.getStatus();
            if (!STATUS_DRAFT.equals(orderStatus) && !STATUS_SUBMITTED.equals(orderStatus) && !STATUS_SCRAPPED.equals(orderStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parts can only be imported into return orders in 'draft', 'submitted' or 'scrapped' status. Current status: "
                                + orderStatus);
            }

            if (dto.getAnalyst() == null || dto.getAnalyst().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analyst is required");
            }
        }

        // 预先批量生成 partNumber：循环调用 generatePartNumber 会在 saveAll 前多次查询同一 DB max，
        // 导致同批次内所有相同 (bu, platform) 的 part 拿到同一序号。改为 per-prefix 内存计数器，
        // 首次遇到前缀时读一次 DB max，后续在内存递增。
        List<String> batchPartNumbers = generatePartNumbersForBatch(dtos);

        // 构建实体列表
        List<Part> parts = new ArrayList<>(dtos.size());
        for (int idx = 0; idx < dtos.size(); idx++) {
            PartDTO dto = dtos.get(idx);
            Part part = Part.builder()
                    .id(UUID.randomUUID().toString())
                    .orderId(trimText(dto.getOrderId()))
                    .partCode(trimText(dto.getPartCode()))
                    .businessUnit(trimText(dto.getBusinessUnit()))
                    .productPlatform(trimText(dto.getProductPlatform()))
                    .partNumber(batchPartNumbers.get(idx))
                    .partProductionDate(parseDate(dto.getPartProductionDate()))
                    .productionShift(trimText(dto.getProductionShift()))
                    .failureType(trimText(dto.getFailureType()))
                    .boschFailureType(trimText(dto.getBoschFailureType()))
                    .vehicleProductionDate(parseDate(dto.getVehicleProductionDate()))
                    .vehiclePurchaseDate(parseDate(dto.getVehiclePurchaseDate()))
                    .vehicleFailureDate(parseDate(dto.getVehicleFailureDate()))
                    .vehicleVin(trimText(dto.getVehicleVIN()))
                    .vehicleMileage(dto.getVehicleMileage())
                    .customerDescription(trimText(dto.getCustomerDescription()))
                    .otherDescription(trimText(dto.getOtherDescription()))
                    .otherInfo(trimText(dto.getOtherInfo()))
                    .repairStation(trimText(dto.getRepairStation())).complaintLocation(trimText(dto.getComplaintLocation()))
                    .responsibleEngineer(trimText(dto.getResponsibleEngineer()))
                    .analyst(trimText(dto.getAnalyst()))
                    .qcNo(trimText(dto.getQcNo()))
                    .status(STATUS_SCRAPPED)
                    .statusChangedAt(LocalDateTime.now())
                    .build();
            parts.add(part);
        }

        // 批量保存
        List<Part> savedParts = partRepo.saveAll(parts);

        // 批量更新导入时间（如果有）
        Map<String, LocalDateTime> idToCreatedAt = new HashMap<>();
        for (int i = 0; i < dtos.size(); i++) {
            PartDTO dto = dtos.get(i);
            LocalDateTime importCreatedAt = parseDateTime(dto.getCreatedAt());
            if (importCreatedAt != null && i < savedParts.size()) {
                idToCreatedAt.put(savedParts.get(i).getId(), importCreatedAt);
            }
        }

        if (!idToCreatedAt.isEmpty()) {
            for (Map.Entry<String, LocalDateTime> entry : idToCreatedAt.entrySet()) {
                partRepo.updateCreatedAt(entry.getKey(), entry.getValue());
            }
        }

        // 刷新并清理一级缓存，避免内存占用过大
        entityManager.flush();
        entityManager.clear();

        // 批量查询退货单号用于DTO构建
        Set<String> savedOrderIds = savedParts.stream()
                .map(Part::getOrderId)
                .collect(Collectors.toSet());
        Map<String, String> orderIdToNumber = returnOrderRepository.findAllById(savedOrderIds).stream()
                .collect(Collectors.toMap(
                        o -> o.getId(),
                        o -> o.getOrderNumber() != null ? o.getOrderNumber() : ""));

        return savedParts.stream()
                .map(part -> buildDTO(part, orderIdToNumber.get(part.getOrderId())))
                .collect(Collectors.toList());
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim());
    }

    @Transactional
    public PartDTO update(String id, PartDTO dto) {
        Part part = partRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));

        // Check permission: submitted parts (with partNumber) can only be edited by QMC
        // Leader, unless still in initial analysis (暂存状态所有人可编辑)
        if (part.getPartNumber() != null && !STATUS_IN_INITIAL_ANALYSIS.equals(part.getStatus())) {
            boolean isQMCLeader = hasRole(ROLE_QMC_LEADER);
            if (!isQMCLeader) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only QMC Leader can edit submitted parts");
            }
        }

        part.setPartCode(trimText(dto.getPartCode()));
        if (dto.getPartNumber() != null && !dto.getPartNumber().isBlank()) {
            part.setPartNumber(trimText(dto.getPartNumber()));
        }
        part.setBusinessUnit(trimText(dto.getBusinessUnit()));
        part.setProductPlatform(trimText(dto.getProductPlatform()));
        part.setPartProductionDate(parseDate(dto.getPartProductionDate()));
        part.setProductionShift(trimText(dto.getProductionShift()));
        part.setFailureType(trimText(dto.getFailureType()));
        part.setBoschFailureType(trimText(dto.getBoschFailureType()));
        part.setVehicleProductionDate(parseDate(dto.getVehicleProductionDate()));
        part.setVehiclePurchaseDate(parseDate(dto.getVehiclePurchaseDate()));
        part.setVehicleFailureDate(parseDate(dto.getVehicleFailureDate()));
        part.setVehicleVin(trimText(dto.getVehicleVIN()));
        part.setVehicleMileage(dto.getVehicleMileage());
        part.setCustomerDescription(trimText(dto.getCustomerDescription()));
        part.setOtherDescription(trimText(dto.getOtherDescription()));
        part.setRepairStation(trimText(dto.getRepairStation())); part.setComplaintLocation(trimText(dto.getComplaintLocation()));
        part.setResponsibleEngineer(trimText(dto.getResponsibleEngineer()));
        part.setAnalyst(trimText(dto.getAnalyst()));
        partRepo.save(part);
        return toDTO(part);
    }

    @Transactional
    public void delete(String id) {
        Part part = partRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part not found: " + id));

        // Check permission: submitted parts (with partNumber) can only be deleted by
        // QMC Leader, unless still in initial analysis (暂存状态所有人可删除)
        if (part.getPartNumber() != null && !STATUS_IN_INITIAL_ANALYSIS.equals(part.getStatus())) {
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

        // OCR 识别处理中仅允许暂存，不允许提交
        ocrTaskRepo.findByPartIdOrderByCreatedAtDesc(part.getId()).stream().findFirst().ifPresent(task -> {
            if (OcrTask.STATUS_CREATED.equals(task.getStatus()) || OcrTask.STATUS_PROCESSING.equals(task.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "OCR is still processing. Save draft first and submit after recognition completes.");
            }
        });

        // 如果尚未生成零件编号，则生成新编号
        if (part.getPartNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Part number is required before submit");
        }
        // 提交前再次校验唯一性（防并发）
        if (partRepo.existsByOrderIdAndPartNumberAndIdNot(part.getOrderId(), part.getPartNumber(), part.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Part number '" + part.getPartNumber() + "' already exists in this return order");
        }
        // 提交后状态变化：0km → analysis_skipped，非0km → initial_analysis_completed
        if (STATUS_IN_INITIAL_ANALYSIS.equals(part.getStatus())) {
            var returnOrder = returnOrderRepository.findById(part.getOrderId()).orElse(null);
            boolean is0km = returnOrder != null && ComplaintTypeConstants.isZeroKm(returnOrder.getComplaintType());
            part.setStatus(is0km ? STATUS_ANALYSIS_SKIPPED : STATUS_INITIAL_ANALYSIS_COMPLETED);
            part.setStatusChangedAt(LocalDateTime.now());
        }
        // 已提交的单据也可以再次提交（用于更新数据），只保存更新
        try {
            partRepo.save(part);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Part number '" + part.getPartNumber() + "' already exists in this return order");
        }
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

    /**
     * 为整个批次预生成 partNumber，避免循环内逐个调用 generatePartNumber 导致同前缀零件拿到相同序号。
     * 按前缀 (BU-PLATFORM-) 分组，首次遇到某前缀时从 DB 读取 max 序号，后续在内存递增。
     */
    private List<String> generatePartNumbersForBatch(List<PartDTO> dtos) {
        // key = orderId + ":" + prefix，序号按退货单隔离
        Map<String, Integer> orderPrefixNextSeq = new LinkedHashMap<>();
        List<String> result = new ArrayList<>(dtos.size());

        for (PartDTO dto : dtos) {
            String safeBu = (dto.getBusinessUnit() == null || dto.getBusinessUnit().isBlank()) ? "BLANK"
                    : dto.getBusinessUnit();
            String safePlatform = (dto.getProductPlatform() == null || dto.getProductPlatform().isBlank()) ? "BLANK"
                    : dto.getProductPlatform();
            String prefix = safeBu + "-" + safePlatform + "-";
            String orderId = dto.getOrderId();
            String key = orderId + ":" + prefix;

            if (!orderPrefixNextSeq.containsKey(key)) {
                int maxSeq = partRepo.findMaxSeqByPrefixAndOrderId(prefix.length() + 1, prefix + "%", orderId).orElse(0);
                orderPrefixNextSeq.put(key, maxSeq + 1);
            }

            int seq = orderPrefixNextSeq.get(key);
            result.add(prefix + String.format("%04d", seq));
            orderPrefixNextSeq.put(key, seq + 1);
        }

        return result;
    }

    private String generatePartNumber(String bu, String productPlatform, String orderId) {
        String safeBu = (bu == null || bu.isBlank()) ? "BLANK" : bu;
        String safePlatform = (productPlatform == null || productPlatform.isBlank()) ? "BLANK" : productPlatform;
        String prefix = safeBu + "-" + safePlatform + "-";

        int maxRetries = 3;
        int retryDelay = 100;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                int maxSeq = partRepo.findMaxSeqByPrefixAndOrderId(prefix.length() + 1, prefix + "%", orderId).orElse(0);
                return prefix + String.format("%04d", maxSeq + 1);
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < maxRetries) {
                    log.warn("[PartService] generatePartNumber failed (attempt {}/{}), retrying: {}",
                            attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "Part number generation interrupted", ie);
                    }
                    retryDelay *= 2; // 指数退避
                }
            }
        }

        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to generate part number after " + maxRetries + " attempts: " +
                        (lastException != null ? lastException.getMessage() : "Unknown error"),
                lastException);
    }

    private boolean hasRole(String roleName) {
        var headers = CommonHeaderManager.getCommonHeaders();
        if (headers == null || headers.getRoleNames() == null) {
            return false;
        }
        return headers.getRoleNames().contains(roleName);
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
                .partProductionDate(
                        part.getPartProductionDate() != null ? part.getPartProductionDate().toString() : null)
                .productionShift(part.getProductionShift())
                .failureType(part.getFailureType())
                .boschFailureType(part.getBoschFailureType())
                .repairStation(part.getRepairStation()).complaintLocation(part.getComplaintLocation())
                .responsibleEngineer(part.getResponsibleEngineer())
                .analyst(part.getAnalyst())
                .qcNo(part.getQcNo())
                .vehicleProductionDate(
                        part.getVehicleProductionDate() != null ? part.getVehicleProductionDate().toString() : null)
                .vehiclePurchaseDate(
                        part.getVehiclePurchaseDate() != null ? part.getVehiclePurchaseDate().toString() : null)
                .vehicleFailureDate(
                        part.getVehicleFailureDate() != null ? part.getVehicleFailureDate().toString() : null)
                .vehicleVIN(part.getVehicleVin())
                .vehicleMileage(part.getVehicleMileage())
                .customerDescription(part.getCustomerDescription())
                .otherDescription(part.getOtherDescription())
                .otherInfo(part.getOtherInfo())
                .status(part.getStatus())
                .isSample(part.getIsSample())
                .images(parseImages(part.getImages()))
                .createdBy(part.getCreatedBy())
                .createdAt(part.getCreatedAt() != null ? part.getCreatedAt().toString() : null)
                .updatedBy(part.getUpdatedBy())
                .updatedAt(part.getUpdatedAt() != null ? part.getUpdatedAt().toString() : null)
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank())
            return null;
        return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String trimText(String value) {
        return value == null ? null : value.trim();
    }

    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.trim().isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(imagesJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

}
