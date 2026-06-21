package it.unicas.omnimove.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String PREFIX = "login_failed:";

    public boolean isBlocked(String email) {
        String val = redisTemplate.opsForValue().get(PREFIX + email.toLowerCase());
        return val != null && Integer.parseInt(val) >= MAX_ATTEMPTS;
    }

    public void recordFailure(String email) {
        String key = PREFIX + email.toLowerCase();
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            // First failure — start the 15-minute window
            redisTemplate.expire(key, LOCK_DURATION);
        }
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            log.warn("Account temporarily locked after {} failed login attempts: {}", attempts, email);
        }
    }

    public void resetAttempts(String email) {
        redisTemplate.delete(PREFIX + email.toLowerCase());
    }
}
