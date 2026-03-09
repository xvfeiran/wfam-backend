package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ReportTemplateFieldDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates mock analysis report template data.
 * Templates define the structure for detailed analysis forms based on product platform and failure type.
 */
@Component
public class TemplateDataGenerator {

    public List<ReportTemplateDTO> getTemplates() {
        return new ArrayList<>(List.of(
                buildNoiseTemplate(),
                buildFractureTemplate(),
                buildLeakTemplate(),
                buildDefaultTemplate()
        ));
    }

    private static ReportTemplateDTO buildNoiseTemplate() {
        return ReportTemplateDTO.builder()
                .id("template-plt1-noise")
                .name("PLT1-噪音分析模板")
                .productPlatform("PLT1")
                .failureType("噪音")
                .fields(buildNoiseFields())
                .build();
    }

    private static ReportTemplateDTO buildFractureTemplate() {
        return ReportTemplateDTO.builder()
                .id("template-plt1-fracture")
                .name("PLT1-断裂分析模板")
                .productPlatform("PLT1")
                .failureType("断裂")
                .fields(buildFractureFields())
                .build();
    }

    private static ReportTemplateDTO buildLeakTemplate() {
        return ReportTemplateDTO.builder()
                .id("template-plt2-leak")
                .name("PLT2-渗漏分析模板")
                .productPlatform("PLT2")
                .failureType("渗漏")
                .fields(buildLeakFields())
                .build();
    }

    private static ReportTemplateDTO buildDefaultTemplate() {
        return ReportTemplateDTO.builder()
                .id("template-default")
                .name("通用精分析模板")
                .productPlatform("")
                .failureType("")
                .fields(buildCommonFields())
                .build();
    }

    private static List<ReportTemplateFieldDTO> buildNoiseFields() {
        return List.of(
                field("noiseType", "select", "噪音类型", true, List.of("啸叫", "异响", "振动噪音", "其他")),
                field("noiseFrequency", "text", "噪音频率(Hz)", true, null),
                field("failureDescription", "textarea", "失效现象描述", true, null),
                field("rootCause", "textarea", "根本原因分析", true, null),
                field("improvement", "textarea", "改进措施", true, null),
                field("responsibleDept", "select", "责任部门", true, List.of("质量部", "工程部", "生产部", "采购部")),
                field("expectedDate", "date", "预计完成时间", true, null)
        );
    }

    private static List<ReportTemplateFieldDTO> buildFractureFields() {
        return List.of(
                field("fractureType", "select", "断裂类型", true, List.of("疲劳断裂", "脆性断裂", "应力断裂", "其他")),
                field("fractureLocation", "text", "断裂位置", true, null),
                field("failureDescription", "textarea", "失效现象描述", true, null),
                field("rootCause", "textarea", "根本原因分析", true, null),
                field("improvement", "textarea", "改进措施", true, null),
                field("responsibleDept", "select", "责任部门", true, List.of("质量部", "工程部", "生产部", "采购部")),
                field("expectedDate", "date", "预计完成时间", true, null)
        );
    }

    private static List<ReportTemplateFieldDTO> buildLeakFields() {
        return List.of(
                field("leakType", "select", "渗漏类型", true, List.of("油渗漏", "气体渗漏", "液压渗漏", "其他")),
                field("leakLocation", "text", "渗漏位置", true, null),
                field("leakRate", "text", "渗漏量", false, null),
                field("failureDescription", "textarea", "失效现象描述", true, null),
                field("rootCause", "textarea", "根本原因分析", true, null),
                field("improvement", "textarea", "改进措施", true, null),
                field("responsibleDept", "select", "责任部门", true, List.of("质量部", "工程部", "生产部", "采购部")),
                field("expectedDate", "date", "预计完成时间", true, null)
        );
    }

    private static List<ReportTemplateFieldDTO> buildCommonFields() {
        return List.of(
                field("failureMode", "select", "失效模式", true, List.of("电气失效", "机械失效", "材料失效", "其他")),
                field("failureDescription", "textarea", "失效现象描述", true, null),
                field("rootCause", "textarea", "根本原因分析", true, null),
                field("improvement", "textarea", "改进措施", true, null),
                field("responsibleDept", "select", "责任部门", true, List.of("质量部", "工程部", "生产部", "采购部")),
                field("expectedDate", "date", "预计完成时间", true, null)
        );
    }

    private static ReportTemplateFieldDTO field(String name, String type, String label, boolean required, List<String> options) {
        return ReportTemplateFieldDTO.builder()
                .name(name)
                .type(type)
                .label(label)
                .required(required)
                .options(options)
                .build();
    }
}
