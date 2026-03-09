package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.PartDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates mock warranty part data for development and testing.
 */
@Component
public class PartDataGenerator {

    public List<PartDTO> getParts() {
        return new ArrayList<>(List.of(
                buildPart("1", "BU1-PLT1-0001", "1", "RO-2026-0001",
                        "RB-12345-AB", "BU1", "PLT1", "A班", "BA40",
                        "RS-001", "上海", "zhangsan", "lisi", "QC-2026-0001",
                        "2025-06-15", "2025-07-20", "2026-01-10",
                        "LSVAB2183E2123456", 15234,
                        "发动机异响，怠速不稳", "analysis_completed",
                        "李四", "2026-01-16 10:00:00"),

                buildPart("2", "BU1-PLT1-0002", "1", "RO-2026-0001",
                        "RB-12345-AC", "BU1", "PLT1", "B班", "BA41",
                        "RS-002", "北京", "wangwu", "zhaoliu",
                        null, "2025-06-18", "2025-08-10", "2026-01-08",
                        "LSVAB2183E2123457", 12560,
                        "怠速抖动", "in_detailed_analysis",
                        "李四", "2026-01-16 10:30:00"),

                buildPart("3", "BU2-PLT3-0001", "2", "RO-2026-0002",
                        "RB-67890-XY", "BU2", "PLT3",
                        null, "BA20", null, null, null, null,
                        null, "2025-05-20", "2025-06-15", "2026-01-12",
                        "LSVCD4291F3456789", 28900,
                        "传感器读数不准确", "in_initial_analysis",
                        "王五", "2026-01-19 09:00:00"),

                buildPart("4", "BU3-PLT2-0001", "3", "RO-2026-0003",
                        "RB-11111-ZZ", "BU3", "PLT2",
                        null, "BA42", "RS-003", "广州", "qianqi", "zhangsan", "QC-2026-0002",
                        "2025-04-10", "2025-05-05", "2026-01-15",
                        "WVWEF5382G1234567", 35000,
                        "电路板烧毁", "analysis_completed",
                        "赵六", "2026-01-21 11:00:00"),

                buildPart("5", "BU1-PLT4-0001", "4", "RO-2026-0004",
                        "RB-22222-AA", "BU1", "PLT4",
                        null, "BA10", null, null, null, null,
                        null, "2025-07-01", "2025-08-15", "2026-01-18",
                        "WBA3A5C50EF123456", 8500,
                        "连接器松动导致断电", "in_initial_analysis",
                        "钱七", "2026-01-23 14:00:00")
        ));
    }

    private PartDTO buildPart(
            String id, String partNumber, String orderId, String orderNumber,
            String partCode, String businessUnit, String productPlatform,
            String productionShift, String complaintType,
            String repairStation, String complaintLocation,
            String responsibleEngineer, String analyst, String qcNo,
            String vehicleProductionDate, String vehiclePurchaseDate, String vehicleFailureDate,
            String vehicleVIN, int vehicleMileage,
            String customerDescription, String status,
            String createdBy, String createdAt) {

        return PartDTO.builder()
                .id(id)
                .partNumber(partNumber)
                .orderId(orderId)
                .orderNumber(orderNumber)
                .partCode(partCode)
                .businessUnit(businessUnit)
                .productPlatform(productPlatform)
                .productionShift(productionShift)
                .complaintType(complaintType)
                .repairStation(repairStation)
                .complaintLocation(complaintLocation)
                .responsibleEngineer(responsibleEngineer)
                .analyst(analyst)
                .qcNo(qcNo)
                .vehicleProductionDate(vehicleProductionDate)
                .vehiclePurchaseDate(vehiclePurchaseDate)
                .vehicleFailureDate(vehicleFailureDate)
                .vehicleVIN(vehicleVIN)
                .vehicleMileage(vehicleMileage)
                .customerDescription(customerDescription)
                .status(status)
                .images(List.of())
                .createdBy(createdBy)
                .createdAt(createdAt)
                .build();
    }
}
