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
}
