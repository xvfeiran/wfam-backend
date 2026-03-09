package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TaskDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates mock task center data for the dashboard.
 */
@Component
public class TaskDataGenerator {

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
}
