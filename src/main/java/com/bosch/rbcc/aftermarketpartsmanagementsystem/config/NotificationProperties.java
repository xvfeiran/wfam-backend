package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "custom.notification")
public class NotificationProperties {

    private Analysis analysis = new Analysis();
    private Approval approval = new Approval();
    private Frequency frequency = new Frequency();
    private String cron = "0 0 0 * * ?";
    private String emailSuffix = "cn.bosch.com";
    private String qmcLeaderEmails = "";

    @Data
    public static class Analysis {
        private int warningDays = 13;
        private int overdueDays = 21;
    }

    @Data
    public static class Approval {
        private int overdueDays = 3;
    }

    @Data
    public static class Frequency {
        private int warning = 1;
        private int overdue = 3;
        private int approvalReminder = 3;
    }

    public String toEmailAddress(String loginName) {
        if (loginName == null || loginName.isBlank()) return null;
        return loginName.trim() + "@" + emailSuffix;
    }
}
