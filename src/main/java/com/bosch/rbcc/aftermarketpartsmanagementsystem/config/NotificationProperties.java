package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "custom.notification")
public class NotificationProperties {

    private Analysis analysis = new Analysis();
    private Approval approval = new Approval();
    private Frequency frequency = new Frequency();
    private String cron;
    /** 仅 Debug 用：是否启用 Mock QMC Leader 邮箱列表（生产环境必须保持 false）。 */
    private boolean mockQmcLeaderEnabled = false;
    /** 仅 Debug 用：Mock QMC Leader 邮箱，逗号分隔（仅 mockQmcLeaderEnabled=true 时生效）。 */
    private String mockQmcLeaderEmails;
    private Map<String, String> userEmails;

    @Data
    public static class Analysis {
        private int warningDays;
        private int overdueDays;
    }

    @Data
    public static class Approval {
        private int overdueDays;
    }

    @Data
    public static class Frequency {
        private int warning;
        private int overdue;
        private int approvalReminder;
    }
}
