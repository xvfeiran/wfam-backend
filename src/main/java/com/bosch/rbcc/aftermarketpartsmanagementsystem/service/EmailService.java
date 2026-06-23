package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.EmailConfigurationDTO;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class EmailService {

    private static long counter = 0;

    private final EmailConfigurationService configurationService;

    public EmailService(@Lazy EmailConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * 异步发送 HTML 邮件（虚拟线程）
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        dispatch(to, subject, htmlContent, true);
    }

    /**
     * 异步发送纯文本邮件（虚拟线程）
     */
    public void sendTextEmail(String to, String subject, String text) {
        dispatch(to, subject, text, false);
    }

    public boolean isAvailable() {
        return configurationService.hasConfiguration();
    }

    /**
     * 同步发送 HTML 邮件，返回是否成功。
     * <p>与 {@link #sendHtmlEmail} 的异步版不同：此方法会阻塞等待 SMTP 返回结果，
     * 以便调用方按真实结果落库状态（SENT/FAILED）。失败时记录 ERROR 日志、
     * 把 "{@code <地址>: <错误信息>}" 追加到 {@code errorCollector}（供落库 ERROR_MESSAGE 用），不抛异常。
     * <p><b>注意</b>：此方法受 {@code mail.smtp.timeout}（默认 60s）约束，单次调用最长阻塞约 60s。
     * 仅适用于低频、需精确状态的通知流程（见 {@code NotificationService.sendAndLog}）。
     *
     * @param to              收件人邮箱
     * @param subject         主题
     * @param htmlContent     HTML 正文
     * @param errorCollector  失败明细收集器；为 null 时忽略。成功时不写入。
     * @return true=发送成功；false=发送失败（已记日志）
     */
    public boolean sendHtmlEmailSync(String to, String subject, String htmlContent, List<String> errorCollector) {
        if (!configurationService.hasConfiguration()) {
            log.warn("[email] No email configuration, skip sending to {}", to);
            if (errorCollector != null) errorCollector.add(to + ": no email configuration");
            return false;
        }

        EmailConfigurationDTO cfg = configurationService.getConfiguration();
        if (cfg == null || cfg.getEmailFrom() == null || cfg.getEmailFrom().isBlank()) {
            log.warn("[email] No from-address configured, skip sending to {}", to);
            if (errorCollector != null) errorCollector.add(to + ": no from-address configured");
            return false;
        }

        try {
            JavaMailSender mailSender = configurationService.getMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String displayName = cfg.getEmailFromDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                helper.setFrom(cfg.getEmailFrom(), displayName);
            } else {
                helper.setFrom(cfg.getEmailFrom());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("[email] Sent to {} | subject={}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("[email] Failed to {}: {}", to, e.getMessage());
            if (errorCollector != null) errorCollector.add(to + ": " + e.getMessage());
            return false;
        }
    }

    private void dispatch(String to, String subject, String content, boolean html) {
        long seq = ++counter;
        Thread.ofVirtual().name("email-vt-" + seq).start(() -> doSend(to, subject, content, html));
    }

    private void doSend(String to, String subject, String content, boolean html) {
        if (!configurationService.hasConfiguration()) {
            log.warn("[email-vt] No email configuration, skip sending to {}", to);
            return;
        }

        // 与测试邮件保持一致：必须显式设置 From（= 配置的发件邮箱），
        // 否则 SMTP 服务器会使用默认 From，触发 Exchange "Client does not have
        // permissions to send as this sender" (550 5.7.60) 拒信。
        EmailConfigurationDTO cfg = configurationService.getConfiguration();
        if (cfg == null || cfg.getEmailFrom() == null || cfg.getEmailFrom().isBlank()) {
            log.warn("[email-vt] No from-address configured, skip sending to {}", to);
            return;
        }

        try {
            JavaMailSender mailSender = configurationService.getMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String displayName = cfg.getEmailFromDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                helper.setFrom(cfg.getEmailFrom(), displayName);
            } else {
                helper.setFrom(cfg.getEmailFrom());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, html);

            mailSender.send(message);
            log.info("[email-vt] Sent to {} | subject={}", to, subject);
        } catch (Exception e) {
            log.error("[email-vt] Failed to {}: {}", to, e.getMessage());
        }
    }
}
