package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.user;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理", description = "系统用户列表（供责任工程师/分析师下拉选择）")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取系统用户列表", description = "返回 id / loginName / displayName。role=analyst 时只返回分析师，role=cqe 时只返回客户质量工程师")
    @GetMapping
    public List<Map<String, String>> list(@RequestParam(required = false) String role) {
        if ("analyst".equals(role)) {
            return userService.listAnalysts();
        }
        if ("cqe".equals(role)) {
            return userService.listCustomerQualityEngineers();
        }
        return userService.listUsers();
    }
}
