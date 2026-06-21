package it.unicas.cassitrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "jwt_blacklist:";

    public void blacklist(String token, long remainingMs) {
        redisTemplate.opsForValue()
            .set(PREFIX + token, "revoked", Duration.ofMillis(remainingMs));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}
