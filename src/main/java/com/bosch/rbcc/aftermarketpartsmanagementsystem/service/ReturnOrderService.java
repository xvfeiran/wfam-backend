package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.header.CommonHeaderManager;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel.ReturnOrderExcelHandler;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReturnOrderService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_SUBMITTED = "submitted";
    private static final String ROLE_QMC_MANAGER = "W_RBCC_AEP_WFAM_QMC_Manager";

    private final ReturnOrderRepository orderRepo;
    private final PartRepository partRepo;
    private final ReturnOrderExcelHandler excelHandler;

    public Page<ReturnOrderDTO> list(String orderNumber, String customer, String status,
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
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
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
                .map(this::toDTO)
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
        // failureType is required
        ReturnOrder order = ReturnOrder.builder()
                .id(UUID.randomUUID().toString())
                .customerId(dto.getCustomerId())
                .customer(dto.getCustomer()) // 保留客户名称用于显示
                .receiveDate(parseDate(dto.getReceiveDate()))
                .complaintDate(parseDate(dto.getComplaintDate()))
                .returnMethod(dto.getReturnMethod())
                .trackingNumber(dto.getTrackingNumber())
                .returnQuantity(dto.getReturnQuantity())
                .complaintType(dto.getComplaintType())

                .status(STATUS_DRAFT)
                .build();
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public ReturnOrderDTO update(String id, ReturnOrderDTO dto) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));

        // Check permission: non-draft orders can only be edited by QMC Manager
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            boolean isQMCManager = hasRole(ROLE_QMC_MANAGER);
            if (!isQMCManager) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only QMC Manager can edit submitted orders");
            }
        }

        order.setCustomerId(dto.getCustomerId());
        order.setCustomer(dto.getCustomer()); // 保留客户名称用于显示
        order.setReceiveDate(parseDate(dto.getReceiveDate()));
        order.setComplaintDate(parseDate(dto.getComplaintDate()));
        order.setReturnMethod(dto.getReturnMethod());
        order.setTrackingNumber(dto.getTrackingNumber());
        order.setReturnQuantity(dto.getReturnQuantity());
        // complaintType can be updated
        if (dto.getComplaintType() != null) {
            order.setComplaintType(dto.getComplaintType());
        }
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public void delete(String id, boolean cascade) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));

        // Check permission: non-draft orders can only be deleted by QMC Manager
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            boolean isQMCManager = hasRole(ROLE_QMC_MANAGER);
            if (!isQMCManager) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only QMC Manager can delete submitted orders");
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

    private static final int EXPORT_MAX_ROWS = 5000;

    public byte[] exportToExcel(String orderNumber, String customer, String status,
                                 String receiveDateStart, String receiveDateEnd) {
        // 先计算总量，超限则拒绝，避免生成超大文件
        long total = list(orderNumber, customer, status, receiveDateStart, receiveDateEnd,
                org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();

        if (total > EXPORT_MAX_ROWS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "导出数量（" + total + " 条）超过上限 " + EXPORT_MAX_ROWS + " 条，请添加筛选条件缩小范围后重试");
        }

        List<ReturnOrderDTO> orders = list(orderNumber, customer, status, receiveDateStart, receiveDateEnd,
                org.springframework.data.domain.PageRequest.of(0, EXPORT_MAX_ROWS)).getContent();
        return excelHandler.exportToExcel(orders);
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
     * 导入专用：创建退货单并直接提交（状态为 in_initial_analysis）。
     * 退货单号年份取自收货时间，而非当前系统年份。
     * 返回包含 orderId 和 orderNumber 的 DTO。
     */
    @Transactional
    public ReturnOrderDTO createAndSubmitForImport(ReturnOrderDTO dto) {
        LocalDate receiveDate   = parseDate(dto.getReceiveDate());
        LocalDate complaintDate = parseDate(dto.getComplaintDate());

        int year = (receiveDate != null) ? receiveDate.getYear() : LocalDate.now().getYear();
        String orderNumber = generateOrderNumberForYear(year);

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

                .status(STATUS_SUBMITTED)
                .build();
        orderRepo.save(order);
        return toDTO(order);
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

    private PartDTO toPartDTO(Part part) {
        return PartDTO.builder()
                .id(part.getId())
                .partNumber(part.getPartNumber())
                .orderId(part.getOrderId())
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
     * Check if current user has the specified role
     */
    private boolean hasRole(String roleName) {
        var headers = CommonHeaderManager.getCommonHeaders();
        if (headers == null || headers.getRoleNames() == null) {
            return false;
        }
        return headers.getRoleNames().contains(roleName);
    }
}
