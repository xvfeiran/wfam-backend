package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MockDataProvider {

    // ========== Lookup Data ==========

    public List<String> getCustomers() {
        return List.of("一汽大众", "上汽大众", "宝马", "奔驰", "奥迪", "长城汽车", "比亚迪", "吉利汽车");
    }

    public List<String> getBusinessUnits() {
        return List.of("BU1", "BU2", "BU3", "BU4");
    }

    public List<String> getProductPlatforms() {
        return List.of("PLT1", "PLT2", "PLT3", "PLT4", "PLT5");
    }

    public List<String> getFailureTypes() {
        return List.of("噪音", "断裂", "变形", "异响", "渗漏", "其他");
    }

    public List<Map<String, String>> getUsers() {
        return List.of(
                Map.of("id", "user-1", "loginName", "zhangsan", "displayName", "张三"),
                Map.of("id", "user-2", "loginName", "lisi", "displayName", "李四"),
                Map.of("id", "user-3", "loginName", "wangwu", "displayName", "王五"),
                Map.of("id", "user-4", "loginName", "zhaoliu", "displayName", "赵六"),
                Map.of("id", "user-5", "loginName", "qianqi", "displayName", "钱七")
        );
    }

    // ========== Return Orders ==========

    public List<ReturnOrderDTO> getOrders() {
        return new ArrayList<>(List.of(
                ReturnOrderDTO.builder()
                        .id("1").orderNumber("RO-2026-0001").customer("一汽大众")
                        .receiveDate("2026-01-15").complaintDate("2026-01-10")
                        .returnMethod("express").trackingNumber("SF1234567890")
                        .returnQuantity(50).initialAnalysisQuantity(50).detailedAnalysisQuantity(5)
                        .scrappedQuantity(0).qcCreatedQuantity(3).qcNotCreatedQuantity(2)
                        .description("客户反馈产品异响，怠速时异响明显")
                        .status("in_detailed_analysis")
                        .createdBy("张三").createdAt("2026-01-15 09:00:00")
                        .build(),
                ReturnOrderDTO.builder()
                        .id("2").orderNumber("RO-2026-0002").customer("上汽大众")
                        .receiveDate("2026-01-18").complaintDate("2026-01-15")
                        .returnMethod("pickup")
                        .returnQuantity(30).initialAnalysisQuantity(30).detailedAnalysisQuantity(3)
                        .scrappedQuantity(0).qcCreatedQuantity(2).qcNotCreatedQuantity(1)
                        .description("传感器数据异常")
                        .status("in_initial_analysis")
                        .createdBy("李四").createdAt("2026-01-18 10:30:00")
                        .build(),
                ReturnOrderDTO.builder()
                        .id("3").orderNumber("RO-2026-0003").customer("宝马")
                        .receiveDate("2026-01-20").complaintDate("2026-01-18")
                        .returnMethod("express").trackingNumber("YT9876543210")
                        .returnQuantity(20).initialAnalysisQuantity(20).detailedAnalysisQuantity(2)
                        .scrappedQuantity(10).qcCreatedQuantity(2).qcNotCreatedQuantity(0)
                        .description("电路板故障")
                        .status("scrap_in_progress")
                        .createdBy("王五").createdAt("2026-01-20 14:00:00")
                        .build(),
                ReturnOrderDTO.builder()
                        .id("4").orderNumber("RO-2026-0004").customer("奔驰")
                        .receiveDate("2026-01-22").complaintDate("2026-01-20")
                        .returnMethod("express").trackingNumber("ZT1122334455")
                        .returnQuantity(15).initialAnalysisQuantity(15).detailedAnalysisQuantity(0)
                        .scrappedQuantity(0).qcCreatedQuantity(0).qcNotCreatedQuantity(0)
                        .description("接口松动")
                        .status("in_initial_analysis")
                        .createdBy("赵六").createdAt("2026-01-22 16:00:00")
                        .build(),
                ReturnOrderDTO.builder()
                        .id("5").orderNumber("RO-2026-0005").customer("奥迪")
                        .receiveDate("2026-01-25").complaintDate("2026-01-23")
                        .returnMethod("other")
                        .returnQuantity(45).initialAnalysisQuantity(45).detailedAnalysisQuantity(5)
                        .scrappedQuantity(20).qcCreatedQuantity(5).qcNotCreatedQuantity(0)
                        .status("scrapped")
                        .createdBy("钱七").createdAt("2026-01-25 09:30:00")
                        .build()
        ));
    }

    // ========== Parts ==========

    public List<PartDTO> getParts() {
        return new ArrayList<>(List.of(
                PartDTO.builder()
                        .id("1").partNumber("BU1-PLT1-0001").orderId("1").orderNumber("RO-2026-0001")
                        .partCode("RB-12345-AB").businessUnit("BU1").productPlatform("PLT1")
                        .productionShift("A班").complaintType("BA40")
                        .repairStation("RS-001").complaintLocation("上海").responsibleEngineer("zhangsan").analyst("lisi")
                        .qcNo("QC-2026-0001")
                        .vehicleProductionDate("2025-06-15").vehiclePurchaseDate("2025-07-20")
                        .vehicleFailureDate("2026-01-10").vehicleVIN("LSVAB2183E2123456")
                        .vehicleMileage(15234).customerDescription("发动机异响，怠速不稳")
                        .status("analysis_completed").images(List.of())
                        .createdBy("李四").createdAt("2026-01-16 10:00:00")
                        .build(),
                PartDTO.builder()
                        .id("2").partNumber("BU1-PLT1-0002").orderId("1").orderNumber("RO-2026-0001")
                        .partCode("RB-12345-AC").businessUnit("BU1").productPlatform("PLT1")
                        .productionShift("B班").complaintType("BA41")
                        .repairStation("RS-002").complaintLocation("北京").responsibleEngineer("wangwu").analyst("zhaoliu")
                        .vehicleProductionDate("2025-06-18").vehiclePurchaseDate("2025-08-10")
                        .vehicleFailureDate("2026-01-08").vehicleVIN("LSVAB2183E2123457")
                        .vehicleMileage(12560).customerDescription("怠速抖动")
                        .status("in_detailed_analysis").images(List.of())
                        .createdBy("李四").createdAt("2026-01-16 10:30:00")
                        .build(),
                PartDTO.builder()
                        .id("3").partNumber("BU2-PLT3-0001").orderId("2").orderNumber("RO-2026-0002")
                        .partCode("RB-67890-XY").businessUnit("BU2").productPlatform("PLT3")
                        .complaintType("BA20")
                        .vehicleProductionDate("2025-05-20").vehiclePurchaseDate("2025-06-15")
                        .vehicleFailureDate("2026-01-12").vehicleVIN("LSVCD4291F3456789")
                        .vehicleMileage(28900).customerDescription("传感器读数不准确")
                        .status("in_initial_analysis").images(List.of())
                        .createdBy("王五").createdAt("2026-01-19 09:00:00")
                        .build(),
                PartDTO.builder()
                        .id("4").partNumber("BU3-PLT2-0001").orderId("3").orderNumber("RO-2026-0003")
                        .partCode("RB-11111-ZZ").businessUnit("BU3").productPlatform("PLT2")
                        .complaintType("BA42").repairStation("RS-003").complaintLocation("广州")
                        .responsibleEngineer("qianqi").analyst("zhangsan").qcNo("QC-2026-0002")
                        .vehicleProductionDate("2025-04-10").vehiclePurchaseDate("2025-05-05")
                        .vehicleFailureDate("2026-01-15").vehicleVIN("WVWEF5382G1234567")
                        .vehicleMileage(35000).customerDescription("电路板烧毁")
                        .status("analysis_completed").images(List.of())
                        .createdBy("赵六").createdAt("2026-01-21 11:00:00")
                        .build(),
                PartDTO.builder()
                        .id("5").partNumber("BU1-PLT4-0001").orderId("4").orderNumber("RO-2026-0004")
                        .partCode("RB-22222-AA").businessUnit("BU1").productPlatform("PLT4")
                        .complaintType("BA10")
                        .vehicleProductionDate("2025-07-01").vehiclePurchaseDate("2025-08-15")
                        .vehicleFailureDate("2026-01-18").vehicleVIN("WBA3A5C50EF123456")
                        .vehicleMileage(8500).customerDescription("连接器松动导致断电")
                        .status("in_initial_analysis").images(List.of())
                        .createdBy("钱七").createdAt("2026-01-23 14:00:00")
                        .build()
        ));
    }

    // ========== Tasks ==========

    public List<TaskDTO> getTasks() {
        return new ArrayList<>(List.of(
                TaskDTO.builder().id("1").type("initial_analysis").title("待初分析").count(15).priority("medium").build(),
                TaskDTO.builder().id("2").type("detailed_analysis").title("待精分析").count(8).priority("medium").build(),
                TaskDTO.builder().id("3").type("warning").title("精分析预警").count(3).priority("high").build(),
                TaskDTO.builder().id("4").type("overdue").title("精分析超期").count(2).priority("urgent").build(),
                TaskDTO.builder().id("5").type("approval").title("精分析报告待审批").count(5).priority("medium").build(),
                TaskDTO.builder().id("6").type("scrap_confirm").title("报废审批确认").count(4).priority("medium").build()
        ));
    }

    // ========== Report Templates ==========

    public List<ReportTemplateDTO> getTemplates() {
        return new ArrayList<>(List.of(
                ReportTemplateDTO.builder()
                        .id("template-plt1-noise").name("PLT1-噪音分析模板")
                        .productPlatform("PLT1").failureType("噪音")
                        .fields(List.of(
                                ReportTemplateFieldDTO.builder().name("noiseType").type("select").label("噪音类型").required(true).options(List.of("啸叫", "异响", "振动噪音", "其他")).build(),
                                ReportTemplateFieldDTO.builder().name("noiseFrequency").type("text").label("噪音频率(Hz)").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("failureDescription").type("textarea").label("失效现象描述").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("rootCause").type("textarea").label("根本原因分析").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("improvement").type("textarea").label("改进措施").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("responsibleDept").type("select").label("责任部门").required(true).options(List.of("质量部", "工程部", "生产部", "采购部")).build(),
                                ReportTemplateFieldDTO.builder().name("expectedDate").type("date").label("预计完成时间").required(true).build()
                        ))
                        .build(),
                ReportTemplateDTO.builder()
                        .id("template-plt1-fracture").name("PLT1-断裂分析模板")
                        .productPlatform("PLT1").failureType("断裂")
                        .fields(List.of(
                                ReportTemplateFieldDTO.builder().name("fractureType").type("select").label("断裂类型").required(true).options(List.of("疲劳断裂", "脆性断裂", "应力断裂", "其他")).build(),
                                ReportTemplateFieldDTO.builder().name("fractureLocation").type("text").label("断裂位置").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("failureDescription").type("textarea").label("失效现象描述").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("rootCause").type("textarea").label("根本原因分析").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("improvement").type("textarea").label("改进措施").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("responsibleDept").type("select").label("责任部门").required(true).options(List.of("质量部", "工程部", "生产部", "采购部")).build(),
                                ReportTemplateFieldDTO.builder().name("expectedDate").type("date").label("预计完成时间").required(true).build()
                        ))
                        .build(),
                ReportTemplateDTO.builder()
                        .id("template-plt2-leak").name("PLT2-渗漏分析模板")
                        .productPlatform("PLT2").failureType("渗漏")
                        .fields(List.of(
                                ReportTemplateFieldDTO.builder().name("leakType").type("select").label("渗漏类型").required(true).options(List.of("油渗漏", "气体渗漏", "液压渗漏", "其他")).build(),
                                ReportTemplateFieldDTO.builder().name("leakLocation").type("text").label("渗漏位置").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("leakRate").type("text").label("渗漏量").required(false).build(),
                                ReportTemplateFieldDTO.builder().name("failureDescription").type("textarea").label("失效现象描述").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("rootCause").type("textarea").label("根本原因分析").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("improvement").type("textarea").label("改进措施").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("responsibleDept").type("select").label("责任部门").required(true).options(List.of("质量部", "工程部", "生产部", "采购部")).build(),
                                ReportTemplateFieldDTO.builder().name("expectedDate").type("date").label("预计完成时间").required(true).build()
                        ))
                        .build(),
                ReportTemplateDTO.builder()
                        .id("template-default").name("通用精分析模板")
                        .productPlatform("").failureType("")
                        .fields(List.of(
                                ReportTemplateFieldDTO.builder().name("failureMode").type("select").label("失效模式").required(true).options(List.of("电气失效", "机械失效", "材料失效", "其他")).build(),
                                ReportTemplateFieldDTO.builder().name("failureDescription").type("textarea").label("失效现象描述").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("rootCause").type("textarea").label("根本原因分析").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("improvement").type("textarea").label("改进措施").required(true).build(),
                                ReportTemplateFieldDTO.builder().name("responsibleDept").type("select").label("责任部门").required(true).options(List.of("质量部", "工程部", "生产部", "采购部")).build(),
                                ReportTemplateFieldDTO.builder().name("expectedDate").type("date").label("预计完成时间").required(true).build()
                        ))
                        .build()
        ));
    }

    // ========== Analysis Reports ==========

    public List<AnalysisReportDTO> getReports() {
        return new ArrayList<>(List.of(
                AnalysisReportDTO.builder()
                        .id("report-1").partId("4").templateId("template-1")
                        .content(Map.of(
                                "failureMode", "电气失效",
                                "failureDescription", "电路板在高温环境下工作导致元器件损坏",
                                "rootCause", "散热设计不足，长时间高负载运行导致温度过高",
                                "improvement", "优化散热结构，增加散热片面积",
                                "responsibleDept", "工程部",
                                "expectedDate", "2026-02-28"
                        ))
                        .status("approved").summary("电路板高温失效分析报告")
                        .createdBy("赵六").createdAt("2026-01-22 15:00:00")
                        .submittedBy("赵六").submittedAt("2026-01-22 16:00:00")
                        .approvedBy("主管").approvedAt("2026-01-23 10:00:00")
                        .build()
        ));
    }

    // ========== Chart Data ==========

    public List<TrendDataPointDTO> generateTrendData(int days) {
        List<TrendDataPointDTO> data = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            data.add(TrendDataPointDTO.builder()
                    .date(date.format(formatter))
                    .orders(ThreadLocalRandom.current().nextInt(5, 25))
                    .parts(ThreadLocalRandom.current().nextInt(20, 70))
                    .build());
        }
        return data;
    }

    public List<ChartDataItemDTO> getCustomerRanking() {
        return List.of(
                ChartDataItemDTO.builder().name("一汽大众").value(150).build(),
                ChartDataItemDTO.builder().name("上汽大众").value(120).build(),
                ChartDataItemDTO.builder().name("宝马").value(95).build(),
                ChartDataItemDTO.builder().name("奔驰").value(88).build(),
                ChartDataItemDTO.builder().name("奥迪").value(75).build(),
                ChartDataItemDTO.builder().name("长城汽车").value(60).build(),
                ChartDataItemDTO.builder().name("比亚迪").value(45).build(),
                ChartDataItemDTO.builder().name("吉利汽车").value(30).build()
        );
    }

    public List<ChartDataItemDTO> getFailureModeData() {
        return List.of(
                ChartDataItemDTO.builder().name("电气失效").value(35).build(),
                ChartDataItemDTO.builder().name("机械失效").value(28).build(),
                ChartDataItemDTO.builder().name("材料失效").value(20).build(),
                ChartDataItemDTO.builder().name("其他").value(17).build()
        );
    }

    public List<ChartDataItemDTO> getBuDistribution() {
        return List.of(
                ChartDataItemDTO.builder().name("BU1").value(40).build(),
                ChartDataItemDTO.builder().name("BU2").value(25).build(),
                ChartDataItemDTO.builder().name("BU3").value(20).build(),
                ChartDataItemDTO.builder().name("BU4").value(15).build()
        );
    }

    public List<ProcessingTimeDTO> getProcessingTimeData() {
        return List.of(
                ProcessingTimeDTO.builder().stage("初分析").avgDays(2.5).build(),
                ProcessingTimeDTO.builder().stage("抽样").avgDays(1.2).build(),
                ProcessingTimeDTO.builder().stage("精分析").avgDays(5.8).build(),
                ProcessingTimeDTO.builder().stage("审批").avgDays(1.5).build(),
                ProcessingTimeDTO.builder().stage("报废").avgDays(3.2).build()
        );
    }

    // ========== Approval Data ==========

    public List<AnalysisApplicationDTO> getMyAnalysisApplications() {
        return new ArrayList<>(List.of(
                AnalysisApplicationDTO.builder()
                        .id("1").reportNumber("AR-2026-0001").partNumber("BU1-PLT1-0001")
                        .productPlatform("PLT1").failureType("噪音").submitTime("2026-02-01 14:00")
                        .status("pending").summary("怠速噪音异常分析报告").approver("赵六")
                        .content(Map.of("failureMode", "机械失效", "failureDescription", "发动机怠速时产生异常噪音", "rootCause", "轴承磨损导致", "improvement", "更换新轴承", "responsibleDept", "工程部"))
                        .build(),
                AnalysisApplicationDTO.builder()
                        .id("2").reportNumber("AR-2026-0002").partNumber("BU2-PLT3-0001")
                        .productPlatform("PLT3").failureType("渗漏").submitTime("2026-02-02 10:30")
                        .status("approved").summary("油封渗漏分析报告").approver("赵六").approveTime("2026-02-02 15:00")
                        .content(Map.of("failureMode", "材料失效", "failureDescription", "油封老化导致渗漏", "rootCause", "材料选型不当", "improvement", "更换耐久性更好的油封材料", "responsibleDept", "采购部"))
                        .build()
        ));
    }

    public List<AnalysisApplicationDTO> getPendingAnalysisApprovals() {
        return new ArrayList<>(List.of(
                AnalysisApplicationDTO.builder()
                        .id("3").reportNumber("AR-2026-0003").partNumber("BU3-PLT2-0001")
                        .productPlatform("PLT2").failureType("断裂").submitter("钱七")
                        .submitTime("2026-02-03 16:45").status("pending").summary("连接件断裂分析报告")
                        .content(Map.of("failureMode", "疲劳断裂", "failureDescription", "连接件在长期使用后断裂", "rootCause", "设计强度不足", "improvement", "增加截面积，提高强度", "responsibleDept", "工程部"))
                        .build(),
                AnalysisApplicationDTO.builder()
                        .id("4").reportNumber("AR-2026-0004").partNumber("BU1-PLT1-0002")
                        .productPlatform("PLT1").failureType("异响").submitter("张三")
                        .submitTime("2026-02-04 09:30").status("pending").summary("异响问题分析报告")
                        .content(Map.of("failureMode", "机械失效", "failureDescription", "运行时产生异响", "rootCause", "装配不良", "improvement", "改进装配工艺", "responsibleDept", "生产部"))
                        .build()
        ));
    }

    // ========== Dashboard Stats ==========

    public DashboardStatsDTO getDashboardStats() {
        List<ReturnOrderDTO> orders = getOrders();
        List<PartDTO> parts = getParts();
        List<TaskDTO> tasks = getTasks();
        int pendingCount = tasks.stream().mapToInt(TaskDTO::getCount).sum();
        return DashboardStatsDTO.builder()
                .totalOrders(orders.size())
                .totalParts(parts.size())
                .pendingTasks(pendingCount)
                .completionRate(85.5)
                .build();
    }
}
