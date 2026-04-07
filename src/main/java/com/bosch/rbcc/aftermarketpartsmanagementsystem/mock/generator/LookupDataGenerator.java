package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Generates lookup/reference data for the application.
 * Includes customers, business units, product platforms, product categories, failure types, and users.
 */
@Component
public class LookupDataGenerator {

    public List<String> getCustomers() {
        return List.of("一汽大众", "上汽大众", "宝马", "奔驰", "奥迪", "长城汽车", "比亚迪", "吉利汽车");
    }

    public List<String> getBusinessUnits() {
        return List.of("BU1", "BU2", "BU3", "BU4");
    }

    public List<String> getProductPlatforms() {
        return List.of("PLT1", "PLT2", "PLT3", "PLT4", "PLT5");
    }

    public List<String> getProductCategories() {
        return List.of(
            "WSA",   // 汽油机喷嘴
            "WSM",   // 柴油机喷嘴
            "PCE",   // 电子控制
            "ESY",   // 电子系统
            "BRY",   // 制动系统
            "CHG",   // 充电系统
            "STA",   // 起动系统
            "IGN",   // 点火系统
            "FUE",   // 燃油系统
            "EMS"    // 发动机管理系统
        );
    }

    public List<String> getFailureTypes() {
        return List.of(
            "噪音",
            "断裂",
            "变形",
            "异响",
            "渗漏",
            "卡滞",
            "功能失效",
            "损坏",
            "其他"
        );
    }

    public List<Map<String, String>> getUsers() {
        return List.of(
                Map.of("id", "user-1", "loginName", "zhangsan", "displayName", "Zhang San"),
                Map.of("id", "user-2", "loginName", "lisi", "displayName", "Li Si (Analyst)"),
                Map.of("id", "user-3", "loginName", "wangwu", "displayName", "Wang Wu"),
                Map.of("id", "user-4", "loginName", "zhaoliu", "displayName", "Zhao Liu (Analyst)"),
                Map.of("id", "user-5", "loginName", "qianqi", "displayName", "Qian Qi (Analyst)"),
                Map.of("id", "user-6", "loginName", "sunba", "displayName", "Sun Ba")
        );
    }

    public List<Map<String, String>> getAnalysts() {
        return List.of(
                Map.of("id", "user-2", "loginName", "lisi", "displayName", "Li Si (Analyst)"),
                Map.of("id", "user-4", "loginName", "zhaoliu", "displayName", "Zhao Liu (Analyst)"),
                Map.of("id", "user-5", "loginName", "qianqi", "displayName", "Qian Qi (Analyst)")
        );
    }
}
