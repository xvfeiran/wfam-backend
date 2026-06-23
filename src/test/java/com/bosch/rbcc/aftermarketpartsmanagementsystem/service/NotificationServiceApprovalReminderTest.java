package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.NotificationProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.NotificationLog;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.NotificationLogRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceApprovalReminderTest {

    private EmailService emailService;
    private NotificationProperties props;
    private NotificationLogRepository logRepo;
    private PartRepository partRepo;
    private UserEmailService userEmailService;
    private AnalysisOrderRepository analysisOrderRepo;
    private ReturnOrderRepository returnOrderRepo;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        props = new NotificationProperties();
        props.getApproval().setOverdueDays(0);          // 任何过去时间都满足
        props.getFrequency().setApprovalReminder(3);
        logRepo = mock(NotificationLogRepository.class);
        partRepo = mock(PartRepository.class);          // 默认返回空 List，预警/超期不触发
        userEmailService = mock(UserEmailService.class);
        analysisOrderRepo = mock(AnalysisOrderRepository.class);
        returnOrderRepo = mock(ReturnOrderRepository.class);

        service = new NotificationService(emailService, props, logRepo, partRepo,
                userEmailService, analysisOrderRepo, returnOrderRepo);

        when(emailService.sendHtmlEmailSync(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(true);
    }

    /** 435：pending_approval 批次超期 → 发提醒给 QMC Leader，按 analysisOrderId 落库 */
    @Test
    void approvalReminder_pendingOrderOverdue_sendsToQmcLeaderAndLogsByOrderId() {
        AnalysisOrder order = AnalysisOrder.builder()
                .id("order-1").orderId("ro-1").analyst("analyst1")
                .status("pending_approval")
                .statusChangedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(analysisOrderRepo.findByStatusAndStatusChangedAtLessThanEqual(
                eq("pending_approval"), any(LocalDateTime.class)))
                .thenReturn(List.of(order));
        when(logRepo.existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
                eq("order-1"), eq("APPROVAL_REMINDER"), eq("SENT"), any(LocalDateTime.class)))
                .thenReturn(false);
        when(userEmailService.getQmcLeaderEmails()).thenReturn(List.of("leader@cn.bosch.com"));
        when(returnOrderRepo.findById("ro-1")).thenReturn(
                Optional.of(ReturnOrder.builder().id("ro-1").orderNumber("RO-001").build()));

        service.scheduledNotificationCheck();

        verify(emailService).sendHtmlEmailSync(eq("leader@cn.bosch.com"), contains("RO-001"), anyString(), anyList());

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepo).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertEquals("APPROVAL_REMINDER", saved.getNotificationType());
        assertEquals("order-1", saved.getAnalysisOrderId());
        assertNull(saved.getPartId());
        assertEquals("SENT", saved.getStatus());
        assertEquals("leader@cn.bosch.com", saved.getRecipients());
    }

    /** 436 前置：3 天频率窗口内已发过 → 跳过，不再发 */
    @Test
    void approvalReminder_withinFrequencyWindow_skips() {
        AnalysisOrder order = AnalysisOrder.builder()
                .id("order-2").orderId("ro-2").analyst("analyst2")
                .status("pending_approval")
                .statusChangedAt(LocalDateTime.now().minusDays(1))
                .build();
        when(analysisOrderRepo.findByStatusAndStatusChangedAtLessThanEqual(
                eq("pending_approval"), any(LocalDateTime.class)))
                .thenReturn(List.of(order));
        when(logRepo.existsByAnalysisOrderIdAndNotificationTypeAndStatusAndSentAtAfter(
                eq("order-2"), eq("APPROVAL_REMINDER"), eq("SENT"), any(LocalDateTime.class)))
                .thenReturn(true);

        service.scheduledNotificationCheck();

        verify(emailService, never()).sendHtmlEmailSync(anyString(), anyString(), anyString(), anyList());
        verify(logRepo, never()).save(any());
    }

    /** 437：批次已离开 pending_approval → 不在候选集 → 不发 */
    @Test
    void approvalReminder_orderNotPending_notACandidate() {
        when(analysisOrderRepo.findByStatusAndStatusChangedAtLessThanEqual(
                eq("pending_approval"), any(LocalDateTime.class)))
                .thenReturn(List.of());  // 已 approved，候选集为空

        service.scheduledNotificationCheck();

        verify(emailService, never()).sendHtmlEmailSync(anyString(), anyString(), anyString(), anyList());
        verify(logRepo, never()).save(any());
    }
}
