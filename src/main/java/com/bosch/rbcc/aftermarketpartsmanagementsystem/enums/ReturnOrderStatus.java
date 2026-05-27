package com.bosch.rbcc.aftermarketpartsmanagementsystem.enums;

import lombok.Getter;

@Getter
public enum ReturnOrderStatus {
    DRAFT("draft", "草稿"),
    SUBMITTED("submitted", "已提交"),
    REGISTERED("registered", "退件登记/已完成"),
    SCRAPPED("scrapped", "已报废");

    private final String code;
    private final String label;

    ReturnOrderStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ReturnOrderStatus fromCode(String code) {
        for (ReturnOrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown return order status: " + code);
    }
}
