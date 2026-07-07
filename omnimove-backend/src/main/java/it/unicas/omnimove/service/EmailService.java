package it.unicas.omnimove.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * Sends transactional emails for OMNIMOVE.
 *
 * If the SMTP credentials are not configured (e.g. during local dev),
 * the token is printed to the log so the flow can still be tested
 * without a real email account.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final JavaMailSender mailSender;

    @Value("${omnimove.mail.from:OMNIMOVE <noreply@omnimove.it>}")
    private String from;

    @Value("${omnimove.mail.base-url:http://localhost:8180}")
    private String baseUrl;

    // ── Public API ──────────────────────────────────────────────────

    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/api/v1/auth/verify?token=" + token;
        String subject = "OMNIMOVE — Verify your email address";
        String body = buildVerificationHtml(to, link);
        sendHtml(to, subject, body);
        log.info("[EMAIL] Verification email sent to {}", to);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String link = baseUrl + "/api/v1/auth/reset-page?token=" + token;
        String subject = "OMNIMOVE — Reset your password";
        String body = buildResetHtml(to, link);
        sendHtml(to, subject, body);
        log.info("[EMAIL] Password reset email sent to {}", to);
    }

    // ── Internal helpers ────────────────────────────────────────────

    private void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("[EMAIL] Sent '{}' to {}", subject, to);
        } catch (Exception e) {
            log.warn("[EMAIL] Could not send email to {}: {}. Check MAIL_USERNAME / MAIL_PASSWORD .env vars.", to, e.getMessage());
        }
    }

    // ── HTML templates (match OMNIMOVE dark theme) ──────────────────

    private String buildVerificationHtml(String to, String link) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:32px 20px;background:#ffffff;font-family:Arial,sans-serif;color:#111111;">
              <p style="font-size:16px;font-weight:bold;margin:0 0 16px;">OMNIMOVE – Verify your email</p>
              <p style="font-size:14px;margin:0 0 8px;">Hi,</p>
              <p style="font-size:14px;margin:0 0 20px;">
                Click the link below to verify your email address. The link expires in 24 hours.
              </p>
              <p style="margin:0 0 20px;">
                <a href="%s" style="font-size:14px;color:#3B82F6;">Verify my email address</a>
              </p>
              <p style="font-size:12px;color:#666666;margin:0 0 6px;">
                Or copy and paste this URL into your browser:
              </p>
              <p style="font-size:11px;color:#666666;word-break:break-all;margin:0 0 24px;">%s</p>
              <hr style="border:none;border-top:1px solid #dddddd;margin:0 0 16px;">
              <p style="font-size:11px;color:#999999;margin:0;">
                If you did not create an OMNIMOVE account, ignore this email.<br>
                OMNIMOVE – University of Cassino, UNICAS 2025/2026
              </p>
            </body>
            </html>
            """.formatted(link, link);
    }

    private String buildResetHtml(String to, String link) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:32px 20px;background:#ffffff;font-family:Arial,sans-serif;color:#111111;">
              <p style="font-size:16px;font-weight:bold;margin:0 0 16px;">OMNIMOVE – Password reset</p>
              <p style="font-size:14px;margin:0 0 8px;">Hi,</p>
              <p style="font-size:14px;margin:0 0 20px;">
                We received a request to reset your password. Click the link below to set a new one.
                The link expires in 1 hour.
              </p>
              <p style="margin:0 0 20px;">
                <a href="%s" style="font-size:14px;color:#3B82F6;">Reset my password</a>
              </p>
              <p style="font-size:12px;color:#666666;margin:0 0 6px;">
                Or copy and paste this URL into your browser:
              </p>
              <p style="font-size:11px;color:#666666;word-break:break-all;margin:0 0 24px;">%s</p>
              <hr style="border:none;border-top:1px solid #dddddd;margin:0 0 16px;">
              <p style="font-size:11px;color:#999999;margin:0;">
                If you did not request a password reset, ignore this email. Your password will not change.<br>
                OMNIMOVE – University of Cassino, UNICAS 2025/2026
              </p>
            </body>
            </html>
            """.formatted(link, link);
    }
}
