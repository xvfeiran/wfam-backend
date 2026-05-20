package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.AepProxyProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Slf4j
@Service
public class UserService {

    private static final List<String> ANALYST_LIST = Arrays.asList(
        "lisi",      // Li Si (Analyst)
        "qianqi",    // Qian Qi (Analyst)
        "zhaoliu"    // Zhao Liu (Analyst)
    );

    private static final List<String> CQE_LIST = Arrays.asList(
        "zhangsan"   // Zhang San (Customer Quality Engineer)
    );

    private final AepProxyProperties aepProxyProperties;
    @Autowired(required = false)
    private RestClient aepRestClient;
    private final AnalysisOrderRepository analysisOrderRepository;
    private final PartRepository partRepository;
    private final ReturnOrderRepository returnOrderRepository;

    public UserService(AepProxyProperties aepProxyProperties,
                       AnalysisOrderRepository analysisOrderRepository,
                       PartRepository partRepository,
                       ReturnOrderRepository returnOrderRepository) {
        this.aepProxyProperties = aepProxyProperties;
        this.analysisOrderRepository = analysisOrderRepository;
        this.partRepository = partRepository;
        this.returnOrderRepository = returnOrderRepository;
    }

    public List<Map<String, String>> listUsers() {
        if (aepProxyProperties.isEnabled()) {
            return fetchUsersFromAep(null);
        }
        return listMockUsers();
    }

    public List<Map<String, String>> listAnalysts() {
        if (aepProxyProperties.isEnabled()) {
            return fetchUsersFromAep("W_RBCC_AEP_WFAM_Analyst");
        }
        return ANALYST_LIST.stream().map(this::toMockUser).toList();
    }

    public List<Map<String, String>> listCustomerQualityEngineers() {
        if (aepProxyProperties.isEnabled()) {
            return fetchUsersFromAep("W_RBCC_AEP_WFAM_Customer_Quality");
        }
        return CQE_LIST.stream().map(this::toMockUser).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> fetchUsersFromAep(String roleNameEn) {
        Map<String, Object> responseBody = aepRestClient.post()
                .uri(aepProxyProperties.getUserListUri())
                .body(Map.of("pageNo", 1, "pageSize", 999, "appId", 1081))
                .retrieve()
                .body(Map.class);

        if (responseBody == null || !"200".equals(String.valueOf(responseBody.get("code")))) {
            throw new IllegalStateException("AEP user list API returned unexpected response: " + responseBody);
        }

        List<Map<String, Object>> roles = (List<Map<String, Object>>) responseBody.get("data");
        if (roles == null) return List.of();

        TreeSet<String> seen = new TreeSet<>();
        return roles.stream()
                .filter(role -> roleNameEn == null || roleNameEn.equals(role.get("nameEn")))
                .flatMap(role -> {
                    List<Map<String, Object>> users = (List<Map<String, Object>>) role.get("roleUsers");
                    return users != null ? users.stream() : java.util.stream.Stream.<Map<String, Object>>empty();
                })
                .filter(user -> {
                    String nt = (String) user.get("ntAccount");
                    return nt != null && !nt.isEmpty() && seen.add(nt);
                })
                .map(this::toAepUser)
                .toList();
    }

    // --- Mock fallback ---

    private List<Map<String, String>> listMockUsers() {
        TreeSet<String> names = new TreeSet<>();

        for (Part p : partRepository.findAll()) {
            addIfPresent(names, p.getAnalyst());
            addIfPresent(names, p.getResponsibleEngineer());
            addIfPresent(names, p.getCreatedBy());
            addIfPresent(names, p.getUpdatedBy());
        }

        for (ReturnOrder o : returnOrderRepository.findAll()) {
            addIfPresent(names, o.getCreatedBy());
            addIfPresent(names, o.getUpdatedBy());
        }

        for (AnalysisOrder ao : analysisOrderRepository.findAll()) {
            addIfPresent(names, ao.getAnalyst());
            addIfPresent(names, ao.getCreatedBy());
            addIfPresent(names, ao.getUpdatedBy());
        }

        return names.stream().map(this::toMockUser).toList();
    }

    private void addIfPresent(TreeSet<String> names, String value) {
        if (value != null && !value.trim().isEmpty()) {
            names.add(value.trim());
        }
    }

    private Map<String, String> toMockUser(String loginName) {
        Map<String, String> user = new LinkedHashMap<>();
        user.put("id", loginName);
        user.put("loginName", loginName);
        user.put("displayName", loginName);
        return user;
    }

    private Map<String, String> toAepUser(Map<String, Object> raw) {
        Map<String, String> user = new LinkedHashMap<>();
        user.put("id", String.valueOf(raw.get("id")));
        user.put("loginName", (String) raw.get("username"));
        user.put("displayName", (String) raw.get("name"));
        user.put("email", (String) raw.get("email"));
        return user;
    }
}
