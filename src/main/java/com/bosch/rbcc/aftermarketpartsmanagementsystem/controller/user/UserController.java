package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.user;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.MockDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理", description = "系统用户列表（供责任工程师/分析师下拉选择）")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final MockDataProvider mockData;

    @Operation(summary = "获取系统用户列表", description = "返回 id / loginName / displayName，用于责任工程师、分析师下拉")
    @GetMapping
    public List<Map<String, String>> list() {
        return mockData.getUsers();
    }
}
