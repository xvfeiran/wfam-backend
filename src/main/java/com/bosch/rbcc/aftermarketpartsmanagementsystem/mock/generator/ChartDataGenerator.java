package com.bosch.rbcc.aftermarketpartsmanagementsystem.mock.generator;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ChartDataItemDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ProcessingTimeDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.TrendDataPointDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates mock chart data for statistical reports and dashboard visualizations.
 */
@Component
public class ChartDataGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Generates trend data for the specified number of days.
     *
     * @param days number of days to generate data for
     * @return list of trend data points
     */
    public List<TrendDataPointDTO> generateTrendData(int days) {
        List<TrendDataPointDTO> data = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            data.add(TrendDataPointDTO.builder()
                    .date(date.format(DATE_FORMATTER))
                    .orders(randomBetween(5, 25))
                    .parts(randomBetween(20, 70))
                    .build());
        }
        return data;
    }

    /**
     * Returns customer ranking data by return volume.
     */
    public List<ChartDataItemDTO> getCustomerRanking() {
        return List.of(
                item("一汽大众", 150),
                item("上汽大众", 120),
                item("宝马", 95),
                item("奔驰", 88),
                item("奥迪", 75),
                item("长城汽车", 60),
                item("比亚迪", 45),
                item("吉利汽车", 30)
        );
    }

    /**
     * Returns failure mode distribution data.
     */
    public List<ChartDataItemDTO> getFailureModeData() {
        return List.of(
                item("电气失效", 35),
                item("机械失效", 28),
                item("材料失效", 20),
                item("其他", 17)
        );
    }

    /**
     * Returns business unit distribution data.
     */
    public List<ChartDataItemDTO> getBuDistribution() {
        return List.of(
                item("BU1", 40),
                item("BU2", 25),
                item("BU3", 20),
                item("BU4", 15)
        );
    }

    /**
     * Returns average processing time data by stage.
     */
    public List<ProcessingTimeDTO> getProcessingTimeData() {
        return List.of(
                ProcessingTimeDTO.builder().stage("初分析").avgDays(2.5).build(),
                ProcessingTimeDTO.builder().stage("抽样").avgDays(1.2).build(),
                ProcessingTimeDTO.builder().stage("精分析").avgDays(5.8).build(),
                ProcessingTimeDTO.builder().stage("审批").avgDays(1.5).build(),
                ProcessingTimeDTO.builder().stage("报废").avgDays(3.2).build()
        );
    }

    private static ChartDataItemDTO item(String name, int value) {
        return ChartDataItemDTO.builder().name(name).value(value).build();
    }

    private static int randomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }
}
