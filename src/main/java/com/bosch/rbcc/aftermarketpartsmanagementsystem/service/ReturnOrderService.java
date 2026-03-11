package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
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
    private static final String STATUS_IN_INITIAL_ANALYSIS = "in_initial_analysis";
    private static final String STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String STATUS_SCRAP_IN_PROGRESS = "scrap_in_progress";
    private static final String STATUS_SCRAPPED = "scrapped";

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
            if (customer != null && !customer.isBlank()) {
                predicates.add(cb.equal(root.get("customer"), customer));
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
        ReturnOrder order = ReturnOrder.builder()
                .id(UUID.randomUUID().toString())
                .customer(dto.getCustomer())
                .receiveDate(parseDate(dto.getReceiveDate()))
                .complaintDate(parseDate(dto.getComplaintDate()))
                .returnMethod(dto.getReturnMethod())
                .trackingNumber(dto.getTrackingNumber())
                .returnQuantity(dto.getReturnQuantity())
                .description(dto.getDescription())
                .status(STATUS_DRAFT)
                .build();
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public ReturnOrderDTO update(String id, ReturnOrderDTO dto) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        order.setCustomer(dto.getCustomer());
        order.setReceiveDate(parseDate(dto.getReceiveDate()));
        order.setComplaintDate(parseDate(dto.getComplaintDate()));
        order.setReturnMethod(dto.getReturnMethod());
        order.setTrackingNumber(dto.getTrackingNumber());
        order.setReturnQuantity(dto.getReturnQuantity());
        order.setDescription(dto.getDescription());
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public void delete(String id) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only draft orders can be deleted");
        }
        orderRepo.delete(order);
    }

    @Transactional
    public ReturnOrderDTO submit(String id) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not in draft status");
        }
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(STATUS_IN_INITIAL_ANALYSIS);
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public ReturnOrderDTO sampling(String id, List<String> sampledPartIds) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        if (!STATUS_IN_INITIAL_ANALYSIS.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must be in_initial_analysis status for sampling");
        }
        List<Part> parts = partRepo.findByOrderId(id);
        for (Part part : parts) {
            boolean sampled = sampledPartIds != null && sampledPartIds.contains(part.getId());
            part.setIsSample(sampled ? 1 : 0);
            if (sampled) {
                part.setStatus("in_detailed_analysis");
                part.setStatusChangedAt(LocalDateTime.now());
            }
            partRepo.save(part);
        }
        order.setStatus(STATUS_IN_DETAILED_ANALYSIS);
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public ReturnOrderDTO scrap(String id) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        order.setStatus(STATUS_SCRAP_IN_PROGRESS);
        orderRepo.save(order);
        return toDTO(order);
    }

    @Transactional
    public ReturnOrderDTO workonConfirm(String id) {
        ReturnOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
        if (!STATUS_SCRAP_IN_PROGRESS.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must be in scrap_in_progress status");
        }
        order.setStatus(STATUS_SCRAPPED);
        orderRepo.save(order);
        return toDTO(order);
    }

    public List<PartDTO> getPartsForOrder(String orderId) {
        return partRepo.findByOrderId(orderId).stream()
                .map(this::toPartDTO)
                .collect(Collectors.toList());
    }

    public byte[] exportToExcel(String orderNumber, String customer, String status,
                                 String receiveDateStart, String receiveDateEnd) {
        List<ReturnOrderDTO> orders = list(orderNumber, customer, status, receiveDateStart, receiveDateEnd,
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
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
        // 格式：年份后两位 + QMC + 四位序号，如 26QMC0001
        int year = LocalDate.now().getYear();
        String yearSuffix = String.valueOf(year).substring(2); // 获取年份后两位，如 2026 -> 26
        String prefix = yearSuffix + "QMC";
        // startPos: 1-based index after the prefix (e.g. "26QMC" = 5 chars, seq starts at pos 6)
        int maxSeq = orderRepo.findMaxSeqByPrefix(prefix.length() + 1, prefix + "%").orElse(0);
        return prefix + String.format("%04d", maxSeq + 1);
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
                .customer(order.getCustomer())
                .receiveDate(order.getReceiveDate() != null ? order.getReceiveDate().toString() : null)
                .complaintDate(order.getComplaintDate() != null ? order.getComplaintDate().toString() : null)
                .returnMethod(order.getReturnMethod())
                .trackingNumber(order.getTrackingNumber())
                .returnQuantity(order.getReturnQuantity())
                .initialAnalysisQuantity(parts.size())
                .detailedAnalysisQuantity(detailedCount)
                .scrappedQuantity(scrappedCount)
                .qcCreatedQuantity(qcCreated)
                .qcNotCreatedQuantity(qcNotCreated)
                .description(order.getDescription())
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
                .complaintType(part.getComplaintType())
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
