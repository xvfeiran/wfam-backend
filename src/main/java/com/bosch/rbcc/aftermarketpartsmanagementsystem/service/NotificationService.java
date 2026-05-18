package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.NotificationProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.NotificationLog;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.NotificationLogRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String TYPE_WARNING = "WARNING";
    private static final String TYPE_OVERDUE = "OVERDUE";
    private static final String TYPE_APPROVAL_REMINDER = "APPROVAL_REMINDER";
    private static final String TYPE_RESPONSIBILITY = "RESPONSIBILITY";
    private static final String TYPE_ZERO_KM = "ZERO_KM";

    private static final List<String> ZERO_KM_TYPES = List.of("BA10", "BA20", "BA21", "BA30", "BA31");

    private static final String STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";

    private final EmailService emailService;
    private final NotificationProperties props;
    private final NotificationLogRepository logRepo;
    private final PartRepository partRepository;
    private final UserEmailService userEmailService;

    // ========== Event-triggered notifications ==========

    public void sendResponsibilityNotification(String partId, String responsibility) {
        if (responsibility == null || (!responsibility.equalsIgnoreCase("B") && !responsibility.equalsIgnoreCase("O"))) {
            return;
        }

        Part part = partRepository.findById(partId).orElse(null);
        if (part == null) {
            log.warn("Part not found for responsibility notification: {}", partId);
            return;
        }

        List<String> recipients = new ArrayList<>();
        List<String> ccList = new ArrayList<>();

        addEmailIfExists(recipients, part.getAnalyst());
        addEmailIfExists(recipients, part.getResponsibleEngineer());
        addQmcLeaders(ccList);

        if (recipients.isEmpty()) {
            log.warn("No recipients for responsibility notification, partId={}", partId);
            return;
        }

        String subject = String.format("[WFAM] 责任判定通知 - 售后件 %s - 责任判定: %s",
            part.getPartNumber(), responsibility);
        String content = buildResponsibilityEmail(part, responsibility);

        sendAndLog(partId, TYPE_RESPONSIBILITY, recipients, ccList, subject, content);
    }

    public void sendZeroKmNotification(String partId, String orderComplaintType) {
        if (orderComplaintType == null || !ZERO_KM_TYPES.contains(orderComplaintType.toUpperCase())) {
            return;
        }

        Part part = partRepository.findById(partId).orElse(null);
        if (part == null) {
            log.warn("Part not found for 0km notification: {}", partId);
            return;
        }

        List<String> recipients = new ArrayList<>();
        addEmailIfExists(recipients, part.getResponsibleEngineer());

        if (recipients.isEmpty()) {
            log.warn("No recipients for 0km notification, partId={}", partId);
            return;
        }

        String subject = String.format("[WFAM] 0公里退货通知 - 售后件 %s", part.getPartNumber());
        String content = buildZeroKmEmail(part, orderComplaintType);

        sendAndLog(partId, TYPE_ZERO_KM, recipients, List.of(), subject, content);
    }

    // ========== Scheduled notifications ==========

    @Scheduled(cron = "${custom.notification.cron}")
    public void scheduledNotificationCheck() {
        log.info("Scheduled notification check started");
        try {
            sendWarningNotifications();
            sendOverdueNotifications();
            sendApprovalReminderNotifications();
        } catch (Exception e) {
            log.error("Scheduled notification check failed: {}", e.getMessage(), e);
        }
        log.info("Scheduled notification check completed");
    }

    private void sendWarningNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getAnalysis().getWarningDays());
        List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_IN_DETAILED_ANALYSIS, threshold);

        for (Part part : parts) {
            if (shouldSkip(part.getId(), TYPE_WARNING, props.getFrequency().getWarning())) {
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addEmailIfExists(recipients, part.getAnalyst());
            if (recipients.isEmpty()) continue;

            long daysInAnalysis = Duration.between(part.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String subject = String.format("[WFAM] 精分析预警 - 售后件 %s - 已进入精分析 %d 天",
                part.getPartNumber(), daysInAnalysis);
            String content = buildWarningEmail(part, daysInAnalysis);

            sendAndLog(part.getId(), TYPE_WARNING, recipients, List.of(), subject, content);
        }
    }

    private void sendOverdueNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getAnalysis().getOverdueDays());
        List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_IN_DETAILED_ANALYSIS, threshold);

        for (Part part : parts) {
            if (shouldSkip(part.getId(), TYPE_OVERDUE, props.getFrequency().getOverdue())) {
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addEmailIfExists(recipients, part.getAnalyst());

            List<String> ccList = new ArrayList<>();
            addQmcLeaders(ccList);

            if (recipients.isEmpty()) continue;

            long daysInAnalysis = Duration.between(part.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String subject = String.format("[WFAM] 精分析超期 - 售后件 %s - 已超期 %d 天",
                part.getPartNumber(), daysInAnalysis - props.getAnalysis().getOverdueDays());
            String content = buildOverdueEmail(part, daysInAnalysis);

            sendAndLog(part.getId(), TYPE_OVERDUE, recipients, ccList, subject, content);
        }
    }

    private void sendApprovalReminderNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getApproval().getOverdueDays());
        List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_PENDING_APPROVAL, threshold);

        for (Part part : parts) {
            if (shouldSkip(part.getId(), TYPE_APPROVAL_REMINDER, props.getFrequency().getApprovalReminder())) {
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addQmcLeaders(recipients);

            if (recipients.isEmpty()) continue;

            long daysPending = Duration.between(part.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String subject = String.format("[WFAM] 审批超期提醒 - 售后件 %s - 审批已等待 %d 天",
                part.getPartNumber(), daysPending);
            String content = buildApprovalReminderEmail(part, daysPending);

            sendAndLog(part.getId(), TYPE_APPROVAL_REMINDER, recipients, List.of(), subject, content);
        }
    }

    // ========== Frequency control ==========

    private boolean shouldSkip(String partId, String type, int frequencyDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(frequencyDays);
        return logRepo.existsByPartIdAndNotificationTypeAndSentAtAfter(partId, type, cutoff);
    }

    // ========== Send + Log ==========

    private void sendAndLog(String partId, String type, List<String> recipients, List<String> ccList,
                            String subject, String content) {
        String recipientsStr = String.join(";", recipients);
        String ccStr = ccList.isEmpty() ? null : String.join(";", ccList);

        try {
            for (String to : recipients) {
                emailService.sendHtmlEmail(to, subject, content);
            }
            if (!ccList.isEmpty()) {
                for (String cc : ccList) {
                    emailService.sendHtmlEmail(cc, subject, content);
                }
            }
            logRepo.save(NotificationLog.builder()
                .id(UUID.randomUUID().toString())
                .partId(partId)
                .notificationType(type)
                .recipients(recipientsStr)
                .ccRecipients(ccStr)
                .status("SUCCESS")
                .sentAt(LocalDateTime.now())
                .build());
            log.info("Notification sent: type={}, partId={}, recipients={}", type, partId, recipientsStr);
        } catch (Exception e) {
            logRepo.save(NotificationLog.builder()
                .id(UUID.randomUUID().toString())
                .partId(partId)
                .notificationType(type)
                .recipients(recipientsStr)
                .ccRecipients(ccStr)
                .status("FAILED")
                .sentAt(LocalDateTime.now())
                .errorMessage(e.getMessage())
                .build());
            log.error("Notification failed: type={}, partId={}, error={}", type, partId, e.getMessage());
        }
    }

    // ========== Email content builders ==========

    private String buildResponsibilityEmail(Part part, String responsibility) {
        return String.format("""
            <h3>责任判定通知</h3>
            <p>售后件 <strong>%s</strong> 的精分析责任判定结果为：<strong>%s</strong></p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>业务单元</td><td>%s</td></tr>
            <tr><td>产品平台</td><td>%s</td></tr>
            <tr><td>责任判定</td><td>%s</td></tr>
            </table>
            <p>请及时登录 WFAM 系统查看详情。</p>
            """,
            part.getPartNumber(), responsibility,
            part.getPartNumber(), part.getPartCode(),
            part.getBusinessUnit(), part.getProductPlatform(),
            "B".equalsIgnoreCase(responsibility) ? "Bosch" : "OEM");
    }

    private String buildZeroKmEmail(Part part, String complaintType) {
        return String.format("""
            <h3>0公里退货通知</h3>
            <p>收到一笔 0公里退货，请及时关注：</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>业务单元</td><td>%s</td></tr>
            <tr><td>产品平台</td><td>%s</td></tr>
            <tr><td>退货类型</td><td>%s</td></tr>
            </table>
            <p>请及时登录 WFAM 系统处理。</p>
            """,
            part.getPartNumber(), part.getPartCode(),
            part.getBusinessUnit(), part.getProductPlatform(),
            complaintType);
    }

    private String buildWarningEmail(Part part, long days) {
        return String.format("""
            <h3>精分析预警通知</h3>
            <p>售后件 <strong>%s</strong> 精分析已进行 <strong>%d</strong> 天，即将超期。</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>已进行天数</td><td>%d</td></tr>
            <tr><td>超期阈值</td><td>%d 天</td></tr>
            </table>
            <p>请尽快完成精分析报告。</p>
            """,
            part.getPartNumber(), days,
            part.getPartNumber(), part.getPartCode(),
            days, props.getAnalysis().getOverdueDays());
    }

    private String buildOverdueEmail(Part part, long days) {
        return String.format("""
            <h3>精分析超期通知</h3>
            <p>售后件 <strong>%s</strong> 精分析已超期！已进行 <strong>%d</strong> 天（超期阈值 %d 天）。</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>已进行天数</td><td>%d</td></tr>
            <tr><td>超期天数</td><td>%d</td></tr>
            </table>
            <p style="color:red;"><strong>请立即处理！</strong></p>
            """,
            part.getPartNumber(), days, props.getAnalysis().getOverdueDays(),
            part.getPartNumber(), part.getPartCode(),
            days, days - props.getAnalysis().getOverdueDays());
    }

    private String buildApprovalReminderEmail(Part part, long days) {
        return String.format("""
            <h3>审批超期提醒</h3>
            <p>售后件 <strong>%s</strong> 的精分析报告审批已等待 <strong>%d</strong> 天。</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>售后件编号</td><td>%s</td></tr>
            <tr><td>零件号</td><td>%s</td></tr>
            <tr><td>等待审批天数</td><td>%d</td></tr>
            </table>
            <p>请尽快完成审批。</p>
            """,
            part.getPartNumber(), days,
            part.getPartNumber(), part.getPartCode(), days);
    }

    // ========== Helpers ==========

    private void addEmailIfExists(List<String> list, String loginName) {
        userEmailService.getEmail(loginName).ifPresent(list::add);
    }

    private void addQmcLeaders(List<String> list) {
        String emails = props.getQmcLeaderEmails();
        if (emails != null && !emails.isBlank()) {
            Arrays.stream(emails.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .forEach(list::add);
        }
    }
}
