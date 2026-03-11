package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisApplicationDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates mock approval workflow data for analysis reports.
 */
@Component
public class ApprovalDataGenerator {

    /**
     * Returns analysis applications submitted by the current user.
     */
    public List<AnalysisApplicationDTO> getMyAnalysisApplications() {
        return new ArrayList<>(List.of(
                buildApplication(
                        "1", "AR-2026-0001", "BU1-PLT1-0001",
                        "PLT1", "噪音", "2026-02-01 14:00",
                        "pending", "怠速噪音异常分析报告", "赵六", null,
                        Map.of(
                                "failureMode", "机械失效",
                                "failureDescription", "发动机怠速时产生异常噪音",
                                "rootCause", "轴承磨损导致",
                                "improvement", "更换新轴承",
                                "responsibleDept", "工程部"
                        )
                ),
                buildApplication(
                        "2", "AR-2026-0002", "BU2-PLT3-0001",
                        "PLT3", "渗漏", "2026-02-02 10:30",
                        "approved", "油封渗漏分析报告", "赵六", "2026-02-02 15:00",
                        Map.of(
                                "failureMode", "材料失效",
                                "failureDescription", "油封老化导致渗漏",
                                "rootCause", "材料选型不当",
                                "improvement", "更换耐久性更好的油封材料",
                                "responsibleDept", "采购部"
                        )
                )
        ));
    }

    /**
     * Returns analysis applications pending approval by the current user.
     */
    public List<AnalysisApplicationDTO> getPendingAnalysisApprovals() {
        return new ArrayList<>(List.of(
                buildApplication(
                        "3", "AR-2026-0003", "BU3-PLT2-0001",
                        "PLT2", "断裂", "2026-02-03 16:45",
                        "pending", "连接件断裂分析报告", null, null,
                        Map.of(
                                "failureMode", "疲劳断裂",
                                "failureDescription", "连接件在长期使用后断裂",
                                "rootCause", "设计强度不足",
                                "improvement", "增加截面积，提高强度",
                                "responsibleDept", "工程部"
                        ),
                        "钱七"
                ),
                buildApplication(
                        "4", "AR-2026-0004", "BU1-PLT1-0002",
                        "PLT1", "异响", "2026-02-04 09:30",
                        "pending", "异响问题分析报告", null, null,
                        Map.of(
                                "failureMode", "机械失效",
                                "failureDescription", "运行时产生异响",
                                "rootCause", "装配不良",
                                "improvement", "改进装配工艺",
                                "responsibleDept", "生产部"
                        ),
                        "张三"
                )
        ));
    }

    private static AnalysisApplicationDTO buildApplication(
            String id, String reportNumber, String partNumber,
            String productPlatform, String failureType,
            String submitTime, String status, String summary,
            String approver, String approveTime, Map<String, String> content) {
        return buildApplication(id, reportNumber, partNumber, productPlatform,
                failureType, submitTime, status, summary, approver, approveTime, content, null);
    }

    private static AnalysisApplicationDTO buildApplication(
            String id, String reportNumber, String partNumber,
            String productPlatform, String failureType,
            String submitTime, String status, String summary,
            String approver, String approveTime, Map<String, String> content, String submitter) {

        AnalysisApplicationDTO.AnalysisApplicationDTOBuilder builder = AnalysisApplicationDTO.builder()
                .id(id)
                .reportNumber(reportNumber)
                .partNumber(partNumber)
                .productPlatform(productPlatform)
                .failureType(failureType)
                .submitTime(submitTime)
                .status(status)
                .summary(summary)
                .content(content);

        if (submitter != null) {
            builder.submitter(submitter);
        }
        if (approver != null) {
            builder.approver(approver);
        }
        if (approveTime != null) {
            builder.approveTime(approveTime);
        }

        return builder.build();
    }
}
