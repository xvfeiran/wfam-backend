package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.NotificationProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.constant.ComplaintTypeConstants;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.NotificationLog;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.NotificationLogRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    private static final String STATUS_IN_DETAILED_ANALYSIS = "in_detailed_analysis";
    private static final String STATUS_PENDING_APPROVAL = "pending_approval";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED = "FAILED";

    private final EmailService emailService;
    private final NotificationProperties props;
    private final NotificationLogRepository logRepo;
    private final PartRepository partRepository;
    private final UserEmailService userEmailService;
    private final AnalysisOrderRepository analysisOrderRepository;
    private final ReturnOrderRepository returnOrderRepository;

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

        // 收件人去重：analyst 与 responsibleEngineer 可能是同一人，避免重复发送
        Set<String> recipientSet = new LinkedHashSet<>();
        addEmailIfExists(recipientSet, part.getAnalyst());
        addEmailIfExists(recipientSet, part.getResponsibleEngineer());

        // CC 去重：去掉 QMC leaders 中已经作为直接收件人的地址
        Set<String> ccSet = new LinkedHashSet<>();
        addQmcLeaders(ccSet);
        ccSet.removeAll(recipientSet);

        List<String> recipients = new ArrayList<>(recipientSet);
        List<String> ccList = new ArrayList<>(ccSet);

        if (recipients.isEmpty()) {
            log.warn("No recipients for responsibility notification, partId={}", partId);
            return;
        }

        String subject = String.format("[WFAM] 责任判定通知 - 售后件 %s - 责任判定: %s",
            part.getPartNumber(), responsibility);
        String content = buildResponsibilityEmail(part, responsibility);

        sendAndLog(partId, null, TYPE_RESPONSIBILITY, recipients, ccList, subject, content);
    }

    public void sendZeroKmNotification(String partId, String orderComplaintType) {
        if (!ComplaintTypeConstants.isZeroKm(orderComplaintType)) {
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

        sendAndLog(partId, null, TYPE_ZERO_KM, recipients, List.of(), subject, content);
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
        // 超期下界：达到此时间点的件由 sendOverdueNotifications 接管，预警阶段跳过，
        // 避免同一售后件在同一天既收到预警又收到超期邮件。预警窗口 = [warningDays, overdueDays)。
        LocalDateTime overdueThreshold = LocalDateTime.now().minusDays(props.getAnalysis().getOverdueDays());
        List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_IN_DETAILED_ANALYSIS, threshold);

        log.info("WARNING scan: candidateCount={}, threshold={}", parts.size(), threshold);
        for (Part part : parts) {
            log.info("WARNING check: partId={}, partNumber={}, analyst={}, statusChangedAt={}",
                part.getId(), part.getPartNumber(), part.getAnalyst(), part.getStatusChangedAt());
            // 已满足超期条件（statusChangedAt <= overdueThreshold）的件不再发预警
            if (!part.getStatusChangedAt().isAfter(overdueThreshold)) {
                log.info("WARNING skip (already overdue, handled by OVERDUE): partId={}", part.getId());
                continue;
            }
            if (shouldSkip(part.getId(), TYPE_WARNING, props.getFrequency().getWarning())) {
                log.info("WARNING skip (within frequency window {}d): partId={}",
                    props.getFrequency().getWarning(), part.getId());
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addEmailIfExists(recipients, part.getAnalyst());
            if (recipients.isEmpty()) {
                log.warn("WARNING skip (no recipient email): partId={}, analyst={}",
                    part.getId(), part.getAnalyst());
                continue;
            }

            long daysInAnalysis = Duration.between(part.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String subject = String.format("[WFAM] 精分析预警 - 售后件 %s - 已进入精分析 %d 天",
                part.getPartNumber(), daysInAnalysis);
            String content = buildWarningEmail(part, daysInAnalysis);

            sendAndLog(part.getId(), null, TYPE_WARNING, recipients, List.of(), subject, content);
        }
    }

    private void sendOverdueNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getAnalysis().getOverdueDays());
        List<Part> parts = partRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_IN_DETAILED_ANALYSIS, threshold);

        log.info("OVERDUE scan: candidateCount={}, threshold={}", parts.size(), threshold);
        for (Part part : parts) {
            log.info("OVERDUE check: partId={}, partNumber={}, analyst={}, statusChangedAt={}",
                part.getId(), part.getPartNumber(), part.getAnalyst(), part.getStatusChangedAt());
            if (shouldSkip(part.getId(), TYPE_OVERDUE, props.getFrequency().getOverdue())) {
                log.info("OVERDUE skip (within frequency window {}d): partId={}",
                    props.getFrequency().getOverdue(), part.getId());
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addEmailIfExists(recipients, part.getAnalyst());

            List<String> ccList = new ArrayList<>();
            addQmcLeaders(ccList);

            if (recipients.isEmpty()) {
                log.warn("OVERDUE skip (no recipient email): partId={}, analyst={}",
                    part.getId(), part.getAnalyst());
                continue;
            }

            long daysInAnalysis = Duration.between(part.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String subject = String.format("[WFAM] 精分析超期 - 售后件 %s - 已超期 %d 天",
                part.getPartNumber(), daysInAnalysis - props.getAnalysis().getOverdueDays());
            String content = buildOverdueEmail(part, daysInAnalysis);

            sendAndLog(part.getId(), null, TYPE_OVERDUE, recipients, ccList, subject, content);
        }
    }

    private void sendApprovalReminderNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(props.getApproval().getOverdueDays());
        List<AnalysisOrder> orders = analysisOrderRepository.findByStatusAndStatusChangedAtLessThanEqual(
            STATUS_PENDING_APPROVAL, threshold);

        log.info("APPROVAL scan: candidateCount={}, threshold={}", orders.size(), threshold);
        for (AnalysisOrder order : orders) {
            log.info("APPROVAL check: analysisOrderId={}, orderId={}, analyst={}, statusChangedAt={}",
                order.getId(), order.getOrderId(), order.getAnalyst(), order.getStatusChangedAt());
            if (shouldSkipOrder(order.getId(), TYPE_APPROVAL_REMINDER,
                                props.getFrequency().getApprovalReminder())) {
                log.info("APPROVAL skip (within frequency window {}d): analysisOrderId={}",
                    props.getFrequency().getApprovalReminder(), order.getId());
                continue;
            }

            List<String> recipients = new ArrayList<>();
            addQmcLeaders(recipients);
            if (recipients.isEmpty()) {
                log.warn("APPROVAL skip (no QMC leader email): analysisOrderId={}", order.getId());
                continue;
            }

            long daysPending = Duration.between(order.getStatusChangedAt(), LocalDateTime.now()).toDays();
            String orderNumber = returnOrderRepository.findById(order.getOrderId())
                .map(ReturnOrder::getOrderNumber)
                .orElse(order.getOrderId());
            String subject = String.format("[WFAM] 审批超期提醒 - 退货单 %s - 审批已等待 %d 天",
                orderNumber, daysPending);
            String content = buildApprovalReminderEmail(order, orderNumber, daysPending);

            sendAndLog(null, order.getId(), TYPE_APPROVAL_REMINDER, recipients, List.of(), subject, content);
        }
    }

    private boolean shouldSkipOrder(String analysisOrderId, String type, int frequencyDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(frequencyDays);
        // 只认 SENT：FAILED 记录不构成"已发送"，下一轮 cron 会重试。
        return logRepo.existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
            analysisOrderId, type, STATUS_SENT, cutoff);
    }

    // ========== Frequency control ==========

    private boolean shouldSkip(String partId, String type, int frequencyDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(frequencyDays);
        // 只认 SENT：FAILED 记录不构成"已发送"，下一轮 cron 会重试。
        return logRepo.existsByPartIdAndNotificationTypeAndStatusAndSentAtAfter(partId, type, STATUS_SENT, cutoff);
    }

    // ========== Send + Log ==========

    private void sendAndLog(String partId, String analysisOrderId, String type,
                            List<String> recipients, List<String> ccList,
                            String subject, String content) {
        String recipientsStr = String.join(";", recipients);
        String ccStr = ccList.isEmpty() ? null : String.join(";", ccList);

        // 同步发送：按真实结果落库状态。任一收件人/CC 失败 → STATUS=FAILED，
        // shouldSkip 只认 SENT，因此失败记录不进 dedup，下一轮 cron 自动重试。
        List<String> errors = new ArrayList<>();
        for (String to : recipients) {
            emailService.sendHtmlEmailSync(to, subject, content, errors);
        }
        for (String cc : ccList) {
            emailService.sendHtmlEmailSync(cc, subject, content, errors);
        }

        boolean allSent = errors.isEmpty();
        String status = allSent ? STATUS_SENT : STATUS_FAILED;
        String errorMessage = null;
        if (!allSent) {
            errorMessage = String.join("; ", errors);
            if (errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 497) + "...";
            }
        }

        try {
            logRepo.save(NotificationLog.builder()
                .id(UUID.randomUUID().toString())
                .partId(partId)
                .analysisOrderId(analysisOrderId)
                .notificationType(type)
                .recipients(recipientsStr)
                .ccRecipients(ccStr)
                .status(status)
                .sentAt(LocalDateTime.now())
                .errorMessage(errorMessage)
                .build());
        } catch (Exception e) {
            log.warn("Failed to log notification: {}", e.getMessage());
        }
        log.info("Notification dispatched: type={}, partId={}, analysisOrderId={}, recipients={}, status={}",
            type, partId, analysisOrderId, recipientsStr, status);
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

    private String buildApprovalReminderEmail(AnalysisOrder order, String orderNumber, long days) {
        return String.format("""
            <h3>审批超期提醒</h3>
            <p>退货单 <strong>%s</strong>（分析师 %s）的精分析报告审批已等待 <strong>%d</strong> 天。</p>
            <table border="1" cellpadding="5" cellspacing="0">
            <tr><td>退货单号</td><td>%s</td></tr>
            <tr><td>分析师</td><td>%s</td></tr>
            <tr><td>等待审批天数</td><td>%d</td></tr>
            </table>
            <p>请尽快完成审批。</p>
            """,
            orderNumber, order.getAnalyst(), days,
            orderNumber, order.getAnalyst(), days);
    }

    // ========== Helpers ==========

    private void addEmailIfExists(Collection<String> list, String loginName) {
        userEmailService.getEmail(loginName).ifPresent(list::add);
    }

    private void addQmcLeaders(Collection<String> list) {
        list.addAll(userEmailService.getQmcLeaderEmails());
    }
}
