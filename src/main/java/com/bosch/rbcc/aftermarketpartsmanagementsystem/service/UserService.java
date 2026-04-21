package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final List<String> ANALYST_LIST = Arrays.asList(
        "lisi",      // Li Si (Analyst)
        "qianqi",    // Qian Qi (Analyst)
        "zhaoliu"    // Zhao Liu (Analyst)
    );

    private final AnalysisOrderRepository analysisOrderRepository;
    private final PartRepository partRepository;
    private final ReturnOrderRepository returnOrderRepository;

    public List<Map<String, String>> listUsers() {
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

        return names.stream().map(this::toUser).toList();
    }

    public List<Map<String, String>> listAnalysts() {
        return ANALYST_LIST.stream().map(this::toUser).toList();
    }

    private void addIfPresent(TreeSet<String> names, String value) {
        if (value != null && !value.trim().isEmpty()) {
            names.add(value.trim());
        }
    }

    private Map<String, String> toUser(String loginName) {
        Map<String, String> user = new LinkedHashMap<>();
        user.put("id", loginName);
        user.put("loginName", loginName);
        user.put("displayName", loginName);
        return user;
    }
}
