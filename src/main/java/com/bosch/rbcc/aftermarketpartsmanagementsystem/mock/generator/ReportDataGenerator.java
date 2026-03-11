package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisReportDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates mock analysis report data.
 */
@Component
public class ReportDataGenerator {

    public List<AnalysisReportDTO> getReports() {
        return new ArrayList<>(List.of(
                AnalysisReportDTO.builder()
                        .id("report-1")
                        .partId("4")
                        .templateId("template-1")
                        .content(Map.<String, Object>of(
                                "failureMode", "电气失效",
                                "failureDescription", "电路板在高温环境下工作导致元器件损坏",
                                "rootCause", "散热设计不足，长时间高负载运行导致温度过高",
                                "improvement", "优化散热结构，增加散热片面积",
                                "responsibleDept", "工程部",
                                "expectedDate", "2026-02-28"
                        ))
                        .status("approved")
                        .summary("电路板高温失效分析报告")
                        .createdBy("赵六")
                        .createdAt("2026-01-22 15:00:00")
                        .submittedBy("赵六")
                        .submittedAt("2026-01-22 16:00:00")
                        .approvedBy("主管")
                        .approvedAt("2026-01-23 10:00:00")
                        .build()
        ));
    }
}
