package com.bosch.rbcc.aftermarketpartsmanagementsystem.enums;

import lombok.Getter;
import java.util.List;

/**
 * 客户失效类型（Customer Failure Type）
 * 用于零件的客户失效类型下拉选项、精分析模板匹配等场景
 */
@Getter
public enum FailureType {
    NVH("NVH"),
    APPEARANCE("APPEARANCE"),
    FUNCTION("FUNCTION");

    private final String code;

    FailureType(String code) {
        this.code = code;
    }

    public static FailureType fromCode(String code) {
        for (FailureType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown failure type: " + code);
    }

    public static List<String> allCodes() {
        return java.util.Arrays.stream(values()).map(FailureType::getCode).toList();
    }
}
