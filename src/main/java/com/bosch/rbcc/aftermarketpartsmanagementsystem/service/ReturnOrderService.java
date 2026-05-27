package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.constant.ComplaintTypeConstants;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Customer;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.enums.ReturnOrderStatus;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.CustomerRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.ExportProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel.ReturnOrderExcelHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReturnOrderService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SUBMITTED = "submitted";
    private static final String STATUS_REGISTERED = "registered";
    private static final String ROLE_QMC_LEADER = "W_RBCC_AEP_WFAM_QMC_Leader";

    private final ReturnOrderRepository orderRepo;
    private final PartRepository partRepo;
    private final CustomerRepository customerRepo;
    private final AnalysisOrderRepository analysisOrderRepo;
    private final ReturnOrderExcelHandler excelHandler;
    private final ExportProperties exportProperties;
    private final EntityManager entityManager;

    public Page<ReturnOrderDTO> list(String orderNumber, String customer, List<String> statuses,
                                      String receiveDateStart, String receiveDateEnd, Pageable pageable) {
        Page<ReturnOrder> page = orderRepo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (orderNumber != null && !orderNumber.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("orderNumber")), "%" + orderNumber.toUpperCase() + "%"));
            }
            // Support both customerId (for API) and customer (for backward compatibility)
            if (customer != null && !customer.isBlank()) {
                predicates.add(cb.or(
                    cb.equal(root.get("customerId"), customer),
                    cb.equal(root.get("customer"), customer)
                ));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (receiveDateStart != null && !receiveDateStart.isBlank()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receiveDate"), LocalDate.parse(receiveDateStart)));
            }
            if (receiveDateEnd != null && !receiveDateEnd.isBlank()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("receiveDate"), LocalDate.parse(receiveDateEnd)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        List<ReturnOrderDTO> dtos = page.getContent().stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public ReturnOrderDTO getById(String id) {
        return orderRepo.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    @Transactional
    public ReturnOrderDTO create(ReturnOrderDTO dto) {
        String customerName = dto.getCustomer();
        if (customerName == null || customerName.isBlank()) {
            customerName = getCustomerName(dto.getCustomerId());
        }

        ReturnOrder order = ReturnOrder.builder()
                .id(UUID.randomUUID().toString())
                .customerId(dto.getCustomerId())
                .customer(customerName)
                .receiveDate(parseDate(dto.getReceiveDate()))
                .complaintDate(parseDate(dto.getComplaintDate()))
                .returnMethod(dto.getReturnMethod())
                .trackingNumber(dto.getTrackingNumber())
                .returnQuantity(dto.getReturnQuantity())
                .complaintType(dto.getComplaintType())
                .otherInfo(dto.getOtherInfo())

                .status(STATUS_DRAFT)
                .build();
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public ReturnOrderDTO update(String id, ReturnOrderDTO dto) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));

        // Check if return order is scrapped
        if (ReturnOrderStatus.SCRAPPED.getCode().equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已报废的退货单不允许编辑");
        }

        // Check permission: non-draft orders can only be edited by QMC Leader
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            boolean isQMCLeader = hasRole(ROLE_QMC_LEADER);
            if (!isQMCLeader) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only QMC Leader can edit submitted orders");
            }
        }

        order.setCustomerId(dto.getCustomerId());

        String customerName = dto.getCustomer();
        if (customerName == null || customerName.isBlank()) {
            customerName = getCustomerName(dto.getCustomerId());
        }
        order.setCustomer(customerName);

        order.setReceiveDate(parseDate(dto.getReceiveDate()));
        order.setComplaintDate(parseDate(dto.getComplaintDate()));
        order.setReturnMethod(dto.getReturnMethod());
        order.setTrackingNumber(dto.getTrackingNumber());
        order.setReturnQuantity(dto.getReturnQuantity());
        // complaintType can be updated
        if (dto.getComplaintType() != null) {
            order.setComplaintType(dto.getComplaintType());
        }
        order.setOtherInfo(dto.getOtherInfo());
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public void delete(String id, boolean cascade) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));

        // Check if return order is scrapped
        if (ReturnOrderStatus.SCRAPPED.getCode().equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已报废的退货单不允许删除");
        }

        // Check permission: non-draft orders can only be deleted by QMC Leader
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            boolean isQMCLeader = hasRole(ROLE_QMC_LEADER);
            if (!isQMCLeader) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only QMC Leader can delete submitted orders");
            }
        }

        // Check for associated parts
        List<Part> parts = partRepo.findByOrderId(id);

        if (!parts.isEmpty()) {
            if (cascade) {
                // Cascade delete: delete all associated parts first
                partRepo.deleteAll(parts);
            } else {
                // Non-cascade: throw error
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete return order with existing parts. Use ?cascade=true to delete with all associated parts.");
            }
        }

        orderRepo.delete(order);
    }

    // Keep backward compatibility
    public void delete(String id) {
        delete(id, false);
    }

    public long getPartsCount(String orderId) {
        return partRepo.countByOrderId(orderId);
    }

    @Transactional
    public ReturnOrderDTO submit(String id) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not in draft status");
        }
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(STATUS_SUBMITTED);
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public ReturnOrderDTO endEntry(String id) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        if (!STATUS_SUBMITTED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not in submitted status");
        }

        // Validate: must have at least one part
        List<Part> parts = partRepo.findByOrderId(id);
        if (parts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot end entry without parts");
        }

        // Check if analysis orders already exist (idempotent)
        if (analysisOrderRepo.countByOrderId(id) > 0) {
            return toDTO(order);
        }

        // Batch-create analysis orders grouped by analyst
        Set<String> analysts = parts.stream()
                .map(Part::getAnalyst)
                .filter(a -> a != null && !a.isBlank())
                .collect(Collectors.toSet());
        for (String analyst : analysts) {
            if (analysisOrderRepo.findByOrderIdAndAnalyst(id, analyst).isEmpty()) {
                String initialStatus = ComplaintTypeConstants.isZeroKm(order.getComplaintType())
                        ? "analysis_completed"
                        : "pending_sampling";
                AnalysisOrder ao = AnalysisOrder.builder()
                        .id(UUID.randomUUID().toString())
                        .orderId(id)
                        .analyst(analyst)
                        .status(initialStatus)
                        .statusChangedAt(LocalDateTime.now())
                        .build();
                analysisOrderRepo.save(ao);
            }
        }

        order.setStatus(STATUS_REGISTERED);
        orderRepo.save(order);

        return toDTO(order);
    }

    public Page<PartDTO> getPartsForOrder(String orderId, String keyword, String businessUnit,
                                           String productPlatform, String status, String analyst,
                                           Pageable pageable) {
        Page<Part> page = partRepo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("orderId"), orderId));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toUpperCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.upper(root.get("partNumber")), pattern),
                    cb.like(cb.upper(root.get("partCode")), pattern)
                ));
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
                predicates.add(cb.equal(root.get("analyst"), analyst));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        List<PartDTO> dtos = page.getContent().stream()
                .map(this::toPartDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public byte[] exportToExcel(String orderNumber, String customer, String status,
                                 String receiveDateStart, String receiveDateEnd) {
        List<String> statuses = (status != null && !status.isBlank()) ? List.of(status) : null;

        var orderSpec = buildOrderSpec(orderNumber, customer, statuses, receiveDateStart, receiveDateEnd);

        List<ReturnOrder> matchingOrders = orderRepo.findAll(orderSpec);
        List<String> orderIds = matchingOrders.stream().map(ReturnOrder::getId).toList();
        long totalParts = orderIds.isEmpty() ? 0 : partRepo.countByOrderIdIn(orderIds);

        int maxRows = exportProperties.getMaxRows();
        if (totalParts > maxRows) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "导出数据量（" + totalParts + " 条）超过上限 " + maxRows + " 条，请缩小筛选条件范围");
        }

        List<ReturnOrderExcelHandler.ExportRow> rows = new ArrayList<>();
        Map<String, ReturnOrderDTO> orderDtoCache = new LinkedHashMap<>();
        for (ReturnOrder order : matchingOrders) {
            ReturnOrderDTO orderDto = orderDtoCache.computeIfAbsent(order.getId(),
                    id -> toDTO(orderRepo.findById(id).orElseThrow()));
            List<Part> parts = partRepo.findByOrderId(order.getId());
            for (Part part : parts) {
                rows.add(new ReturnOrderExcelHandler.ExportRow(orderDto, toPartDTO(part)));
            }
        }

        return excelHandler.exportToExcel(rows);
    }

    private org.springframework.data.jpa.domain.Specification<ReturnOrder> buildOrderSpec(
            String orderNumber, String customer, List<String> statuses,
            String receiveDateStart, String receiveDateEnd) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (orderNumber != null && !orderNumber.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("orderNumber")), "%" + orderNumber.toUpperCase() + "%"));
            }
            if (customer != null && !customer.isBlank()) {
                predicates.add(cb.or(
                    cb.equal(root.get("customerId"), customer),
                    cb.equal(root.get("customer"), customer)
                ));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (receiveDateStart != null && !receiveDateStart.isBlank()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receiveDate"), LocalDate.parse(receiveDateStart)));
            }
            if (receiveDateEnd != null && !receiveDateEnd.isBlank()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("receiveDate"), LocalDate.parse(receiveDateEnd)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    public Map<String, Integer> importFromExcel(MultipartFile file) {
        List<ReturnOrderDTO> orders = excelHandler.importFromExcel(file);
        int success = 0;
        int fail = 0;

        for (ReturnOrderDTO dto : orders) {
            try {
                create(dto);
                success++;
            } catch (Exception e) {
                fail++;
            }
        }

        return Map.of("success", success, "fail", fail);
    }

    private String generateOrderNumber() {
        return generateOrderNumberForYear(LocalDate.now().getYear());
    }

    private String generateOrderNumberForYear(int year) {
        // 格式：年份后两位 + QMC + 四位序号，如 26QMC0001
        String yearSuffix = String.valueOf(year).substring(2);
        String prefix = yearSuffix + "QMC";
        // startPos: 1-based index after the prefix (e.g. "26QMC" = 5 chars, seq starts at pos 6)
        int maxSeq = orderRepo.findMaxSeqByPrefix(prefix.length() + 1, prefix + "%").orElse(0);
        return prefix + String.format("%04d", maxSeq + 1);
    }

    /**
     * 导入专用：创建退货单并直接提交。
     * 若 DTO 已带退货单号，则优先使用 Excel 中的退货单号；否则按年份规则生成。
     * 返回包含 orderId 和 orderNumber 的 DTO。
     */
    @Transactional
    public ReturnOrderDTO createAndSubmitForImport(ReturnOrderDTO dto) {
        LocalDate receiveDate   = parseDate(dto.getReceiveDate());
        LocalDate complaintDate = parseDate(dto.getComplaintDate());
        LocalDateTime importCreatedAt = parseDateTime(dto.getCreatedAt());

        String orderNumber = dto.getOrderNumber();
        if (orderNumber != null) {
            orderNumber = orderNumber.trim();
        }
        if (orderNumber == null || orderNumber.isBlank()) {
            int year = (receiveDate != null) ? receiveDate.getYear() : LocalDate.now().getYear();
            orderNumber = generateOrderNumberForYear(year);
        }
        if (orderRepo.findByOrderNumber(orderNumber).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "退货单号已存在: " + orderNumber);
        }

        ReturnOrder order = ReturnOrder.builder()
                .id(UUID.randomUUID().toString())
                .orderNumber(orderNumber)
                .customerId(dto.getCustomerId())
                .customer(dto.getCustomer())
                .receiveDate(receiveDate)
                .complaintDate(complaintDate)
                .returnMethod(dto.getReturnMethod())
                .trackingNumber(dto.getTrackingNumber())
                .returnQuantity(dto.getReturnQuantity())
                .complaintType(dto.getComplaintType())
                .otherInfo(dto.getOtherInfo())

                .status(STATUS_SUBMITTED)
                .build();
        orderRepo.save(order);

        if (importCreatedAt != null) {
            orderRepo.updateCreatedAt(order.getId(), importCreatedAt);
            order.setCreatedAt(importCreatedAt);
        }

        return toDTO(order);
    }

    /**
     * 批量导入专用：批量创建退货单并直接提交。
     * 使用 Hibernate 批量插入优化性能，每批最多200条。
     *
     * @param dtos 退货单DTO列表
     * @return 成功创建的退货单DTO列表
     */
    @Transactional
    public List<ReturnOrderDTO> createAndSubmitBatchForImport(List<ReturnOrderDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }

        // 收集所有需要生成的单号的年份
        Map<Integer, List<ReturnOrderDTO>> ordersByYear = dtos.stream()
            .collect(Collectors.groupingBy(dto -> {
                LocalDate receiveDate = parseDate(dto.getReceiveDate());
                return (receiveDate != null) ? receiveDate.getYear() : LocalDate.now().getYear();
            }));

        // 为每个年份预生成单号范围
        Map<Integer, List<String>> orderNumbersByYear = new HashMap<>();
        for (Map.Entry<Integer, List<ReturnOrderDTO>> entry : ordersByYear.entrySet()) {
            int year = entry.getKey();
            List<String> orderNumbers = new ArrayList<>();
            for (ReturnOrderDTO dto : entry.getValue()) {
                String orderNumber = dto.getOrderNumber();
                if (orderNumber != null) {
                    orderNumber = orderNumber.trim();
                }
                if (orderNumber == null || orderNumber.isBlank()) {
                    // 按年份规则生成单号
                    orderNumber = generateOrderNumberForYear(year);
                } else {
                    // 使用Excel中的单号，需要检查是否存在
                    if (orderRepo.findByOrderNumber(orderNumber).isPresent()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "退货单号已存在: " + orderNumber);
                    }
                }
                orderNumbers.add(orderNumber);
            }
            orderNumbersByYear.put(year, orderNumbers);
        }

        // 构建实体列表
        List<ReturnOrder> orders = new ArrayList<>(dtos.size());
        Iterator<ReturnOrderDTO> dtoIterator = dtos.iterator();
        Iterator<String> numberIterator = orderNumbersByYear.values().stream()
            .flatMap(List::stream)
            .iterator();

        while (dtoIterator.hasNext() && numberIterator.hasNext()) {
            ReturnOrderDTO dto = dtoIterator.next();
            String orderNumber = numberIterator.next();

            LocalDate receiveDate   = parseDate(dto.getReceiveDate());
            LocalDate complaintDate = parseDate(dto.getComplaintDate());
            LocalDateTime importCreatedAt = parseDateTime(dto.getCreatedAt());

            ReturnOrder order = ReturnOrder.builder()
                    .id(UUID.randomUUID().toString())
                    .orderNumber(orderNumber)
                    .customerId(dto.getCustomerId())
                    .customer(dto.getCustomer())
                    .receiveDate(receiveDate)
                    .complaintDate(complaintDate)
                    .returnMethod(dto.getReturnMethod())
                    .trackingNumber(dto.getTrackingNumber())
                    .returnQuantity(dto.getReturnQuantity())
                    .complaintType(dto.getComplaintType())
                    .otherInfo(dto.getOtherInfo())
                    .status(STATUS_SUBMITTED)
                    .build();
            orders.add(order);
        }

        // 批量保存
        List<ReturnOrder> savedOrders = orderRepo.saveAll(orders);

        // 批量更新导入时间（如果有）
        List<String> idsToUpdate = savedOrders.stream()
            .filter(order -> {
                ReturnOrderDTO dto = findDtoByOrderNumber(dtos, order.getOrderNumber());
                LocalDateTime importCreatedAt = parseDateTime(dto != null ? dto.getCreatedAt() : null);
                return importCreatedAt != null;
            })
            .map(ReturnOrder::getId)
            .collect(Collectors.toList());

        if (!idsToUpdate.isEmpty()) {
            for (String id : idsToUpdate) {
                ReturnOrderDTO dto = findDtoByOrderNumber(dtos,
                    savedOrders.stream()
                        .filter(o -> o.getId().equals(id))
                        .findFirst()
                        .map(ReturnOrder::getOrderNumber)
                        .orElse(null));
                if (dto != null) {
                    LocalDateTime importCreatedAt = parseDateTime(dto.getCreatedAt());
                    orderRepo.updateCreatedAt(id, importCreatedAt);
                }
            }
        }

        // 刷新并清理一级缓存，避免内存占用过大
        entityManager.flush();
        entityManager.clear();

        return savedOrders.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    private ReturnOrderDTO findDtoByOrderNumber(List<ReturnOrderDTO> dtos, String orderNumber) {
        if (orderNumber == null) return null;
        return dtos.stream()
            .filter(dto -> orderNumber.equals(dto.getOrderNumber()) || (dto.getOrderNumber() != null && orderNumber.equals(dto.getOrderNumber().trim())))
            .findFirst()
            .orElse(null);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim());
    }

    private ReturnOrderDTO toDTO(ReturnOrder order) {
        List<Part> parts = partRepo.findByOrderId(order.getId());
        int detailedCount = (int) parts.stream().filter(p -> p.getIsSample() == 1).count();
        int scrappedCount = (int) parts.stream().filter(p -> "scrapped".equals(p.getStatus())).count();
        int qcCreated = (int) parts.stream().filter(p -> p.getQcNo() != null && !p.getQcNo().isBlank()).count();
        int qcNotCreated = parts.size() - qcCreated;

        return ReturnOrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .customer(order.getCustomer())
                .receiveDate(order.getReceiveDate() != null ? order.getReceiveDate().toString() : null)
                .complaintDate(order.getComplaintDate() != null ? order.getComplaintDate().toString() : null)
                .returnMethod(order.getReturnMethod())
                .trackingNumber(order.getTrackingNumber())
                .returnQuantity(order.getReturnQuantity())
                .complaintType(order.getComplaintType())
                .otherInfo(order.getOtherInfo())
                .initialAnalysisQuantity(parts.size())
                .detailedAnalysisQuantity(detailedCount)
                .scrappedQuantity(scrappedCount)
                .qcCreatedQuantity(qcCreated)
                .qcNotCreatedQuantity(qcNotCreated)

                .status(order.getStatus())
                .createdBy(order.getCreatedBy())
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .updatedBy(order.getUpdatedBy())
                .updatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null)
                .build();
    }

    /**
     * 轻量级 DTO 转换，不查询 Part 表，用于列表页。
     * 统计字段设为默认值（列表页不显示这些字段）。
     */
    private ReturnOrderDTO toListDTO(ReturnOrder order) {
        return ReturnOrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .customer(order.getCustomer())
                .receiveDate(order.getReceiveDate() != null ? order.getReceiveDate().toString() : null)
                .complaintDate(order.getComplaintDate() != null ? order.getComplaintDate().toString() : null)
                .returnMethod(order.getReturnMethod())
                .trackingNumber(order.getTrackingNumber())
                .returnQuantity(order.getReturnQuantity())
                .complaintType(order.getComplaintType())
                .otherInfo(order.getOtherInfo())
                .initialAnalysisQuantity(0)
                .detailedAnalysisQuantity(0)
                .scrappedQuantity(0)
                .qcCreatedQuantity(0)
                .qcNotCreatedQuantity(0)

                .status(order.getStatus())
                .createdBy(order.getCreatedBy())
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .updatedBy(order.getUpdatedBy())
                .updatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null)
                .build();
    }

    private PartDTO toPartDTO(Part part) {
        return PartDTO.builder()
                .id(part.getId())
                .partNumber(part.getPartNumber())
                .orderId(part.getOrderId())
                .partCode(part.getPartCode())
                .businessUnit(part.getBusinessUnit())
                .productPlatform(part.getProductPlatform())
                .partProductionDate(part.getPartProductionDate() != null ? part.getPartProductionDate().toString() : null)
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
                .otherInfo(part.getOtherInfo())
                .status(part.getStatus())
                .images(List.of())
                .isSample(part.getIsSample())
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

    /**
     * 根据客户ID获取客户名称
     */
    private String getCustomerName(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return null;
        }
        return customerRepo.findById(customerId)
                .map(Customer::getName)
                .orElse(null);
    }

    /**
     * Check if current user has the specified role
     */
    private boolean hasRole(String roleName) {
        var headers = CommonHeaderManager.getCommonHeaders();
        if (headers == null || headers.getRoleNames() == null) {
            return false;
        }
        return headers.getRoleNames().contains(roleName);
    }

    /**
     * Update return order status
     */
    @Transactional
    public void updateStatus(String orderId, ReturnOrderStatus status) {
        ReturnOrder order = orderRepo.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Return order not found: " + orderId));
        order.setStatus(status.getCode());
        orderRepo.save(order);
    }

    /**
     * Check if all analysis orders for a return order are scrapped.
     * If so, update return order status to scrapped.
     */
    public void checkAndUpdateToScrappedIfAllScrapped(String orderId) {
        // Only check if return order is in registered status
        ReturnOrder order = orderRepo.findById(orderId).orElse(null);
        if (order == null || !ReturnOrderStatus.REGISTERED.getCode().equals(order.getStatus())) {
            return;
        }

        // Check if all analysis orders are workon_scrapped
        long totalAnalysisOrders = analysisOrderRepo.countByOrderId(orderId);
        long scrappedAnalysisOrders = analysisOrderRepo.countByOrderIdAndStatus(orderId, "workon_scrapped");

        if (totalAnalysisOrders > 0 && totalAnalysisOrders == scrappedAnalysisOrders) {
            updateStatus(orderId, ReturnOrderStatus.SCRAPPED);
        }
    }
}
