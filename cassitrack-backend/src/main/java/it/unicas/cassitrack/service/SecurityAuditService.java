package it.unicas.cassitrack.service;

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
}
