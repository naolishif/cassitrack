package it.unicas.omnimove.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j(topic = "SECURITY_AUDIT")
public class SecurityAuditService {

    public void registration(String email, String ip) {
        log.info("REGISTRATION email={} ip={}", email, ip);
    }

    public void loginSuccess(String email, String ip) {
        log.info("LOGIN_SUCCESS email={} ip={}", email, ip);
    }

    public void loginFailure(String email, String ip) {
        log.warn("LOGIN_FAILURE email={} ip={}", email, ip);
    }

    public void accountLocked(String email) {
        log.warn("ACCOUNT_LOCKED email={}", email);
    }

    public void logout(String email) {
        log.info("LOGOUT email={}", email);
    }

    public void emailVerified(String email) {
        log.info("EMAIL_VERIFIED email={}", email);
    }

    public void passwordReset(String email) {
        log.info("PASSWORD_RESET email={}", email);
    }

    public void passwordResetRequested(String email) {
        log.info("PASSWORD_RESET_REQUESTED email={}", email);
    }

    public void accountDeleted(String email) {
        log.warn("ACCOUNT_DELETED email={}", email);
    }

    public void weakPasswordRejected(String email, String ip) {
        log.warn("WEAK_PASSWORD_REJECTED email={} ip={}", email, ip);
    }
}
