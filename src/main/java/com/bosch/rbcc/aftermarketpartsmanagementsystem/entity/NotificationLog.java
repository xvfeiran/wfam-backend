package com.bosch.rbcc.aftermarketpartsmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "APMS_NOTIFICATION_LOG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @Column(name = "ID", length = 36)
    private String id;

    @Column(name = "PART_ID", length = 36, nullable = false)
    private String partId;

    @Column(name = "NOTIFICATION_TYPE", length = 30, nullable = false)
    private String notificationType;

    @Column(name = "RECIPIENTS", length = 500, nullable = false)
    private String recipients;

    @Column(name = "CC_RECIPIENTS", length = 500)
    private String ccRecipients;

    @Column(name = "STATUS", length = 10, nullable = false)
    private String status;

    @Column(name = "SENT_AT", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "ERROR_MESSAGE", length = 500)
    private String errorMessage;
}
