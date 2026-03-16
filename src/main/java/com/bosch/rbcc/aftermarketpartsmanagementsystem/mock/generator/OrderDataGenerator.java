package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReturnOrderDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates mock return order data for development and testing.
 */
@Component
public class OrderDataGenerator {

    public List<ReturnOrderDTO> getOrders() {
        return new ArrayList<>(List.of(
                buildOrder("1", "RO-2026-0001", "一汽大众",
                        "2026-01-15", "2026-01-10", "express", "SF1234567890",
                        50, 50, 5, 0, 3, 2,
                        "客户反馈产品异响，怠速时异响明显", "in_detailed_analysis",
                        "张三", "2026-01-15 09:00:00"),

                buildOrder("2", "RO-2026-0002", "上汽大众",
                        "2026-01-18", "2026-01-15", "pickup",
                        null, 30, 30, 3, 0, 2, 1,
                        "传感器数据异常", "in_initial_analysis",
                        "李四", "2026-01-18 10:30:00"),

                buildOrder("3", "RO-2026-0003", "宝马",
                        "2026-01-20", "2026-01-18", "express", "YT9876543210",
                        20, 20, 2, 10, 2, 0,
                        "电路板故障", "scrap_in_progress",
                        "王五", "2026-01-20 14:00:00"),

                buildOrder("4", "RO-2026-0004", "奔驰",
                        "2026-01-22", "2026-01-20", "express", "ZT1122334455",
                        15, 15, 0, 0, 0, 0,
                        "接口松动", "in_initial_analysis",
                        "赵六", "2026-01-22 16:00:00"),

                buildOrder("5", "RO-2026-0005", "奥迪",
                        "2026-01-25", "2026-01-23", "other",
                        null, 45, 45, 5, 20, 5, 0,
                        "", "scrapped",
                        "钱七", "2026-01-25 09:30:00")
        ));
    }

    private ReturnOrderDTO buildOrder(
            String id, String orderNumber, String customer,
            String receiveDate, String complaintDate, String returnMethod,
            String trackingNumber,
            int returnQuantity, int initialAnalysisQuantity, int detailedAnalysisQuantity,
            int scrappedQuantity, int qcCreatedQuantity, int qcNotCreatedQuantity,
            String description, String status,
            String createdBy, String createdAt) {

        return ReturnOrderDTO.builder()
                .id(id)
                .orderNumber(orderNumber)
                .customer(customer)
                .receiveDate(receiveDate)
                .complaintDate(complaintDate)
                .returnMethod(returnMethod)
                .trackingNumber(trackingNumber)
                .returnQuantity(returnQuantity)
                .initialAnalysisQuantity(initialAnalysisQuantity)
                .detailedAnalysisQuantity(detailedAnalysisQuantity)
                .scrappedQuantity(scrappedQuantity)
                .qcCreatedQuantity(qcCreatedQuantity)
                .qcNotCreatedQuantity(qcNotCreatedQuantity)
                .status(status)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .build();
    }
}
