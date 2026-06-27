package it.unicas.cassitrack.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j(topic = "SECURITY_AUDIT")
public class SecurityAuditService {

    // --- PII masking helpers ---

    /** Masks email to first char + *** + domain: m***@example.com */
    private String maskEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * Truncates the last octet/group of an IP address.
     * IPv4: 192.168.1.123 → 192.168.1.xxx
     * IPv6: 2001:db8::1   → 2001:db8:xxx  (loopback ::1 kept as-is)
     */
    private String maskIp(String ip) {
        if (ip == null) return "null";
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            if (parts.length <= 2) return ip;
            return parts[0] + ":" + parts[1] + ":xxx";
        }
        int last = ip.lastIndexOf('.');
        if (last < 0) return ip;
        return ip.substring(0, last) + ".xxx";
    }

    // --- Audit methods ---

    public void registration(String email, String ip) {
        log.info("REGISTRATION email={} ip={}", maskEmail(email), maskIp(ip));
    }

    public void loginSuccess(String email, String ip) {
        log.info("LOGIN_SUCCESS email={} ip={}", maskEmail(email), maskIp(ip));
    }

    public void loginFailure(String email, String ip) {
        log.warn("LOGIN_FAILURE email={} ip={}", maskEmail(email), maskIp(ip));
    }

    public void accountLocked(String email) {
        log.warn("ACCOUNT_LOCKED email={}", maskEmail(email));
    }

    public void logout(String email) {
        log.info("LOGOUT email={}", maskEmail(email));
    }
}
