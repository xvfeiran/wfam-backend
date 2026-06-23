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

        /**
         * 频率 ≤0 会使 shouldSkip 的 cutoff = now，"sentAt AFTER now" 几乎永不命中 → 永不去重 →
         * 每轮 cron 重复发送。兜底按 1 天处理（仅当配置缺失或误配为 0/负数时生效）。
         */
        public int getWarning() { return Math.max(1, warning); }
        public int getOverdue() { return Math.max(1, overdue); }
        public int getApprovalReminder() { return Math.max(1, approvalReminder); }
    }
}
