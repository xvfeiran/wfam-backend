package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {

    Optional<NotificationLog> findTopByPartIdAndNotificationTypeOrderBySentAtDesc(String partId, String notificationType);

    boolean existsByPartIdAndNotificationTypeAndSentAtAfter(String partId, String notificationType, LocalDateTime after);

    /**
     * 仅统计指定状态（如 SENT）的记录，用于频率去重。
     * FAILED 记录不计入，确保发送失败的通知在下一轮 cron 自动重试。
     */
    boolean existsByPartIdAndNotificationTypeAndStatusAndSentAtAfter(
            String partId, String notificationType, String status, LocalDateTime after);
}
