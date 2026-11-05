package com.blogweb.user.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import com.blogweb.common.exception.BusinessException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final DefaultRedisScript<Long> ATTEMPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], 900) end
            return count
            """, Long.class);
    @Autowired
    private ObjectProvider<StringRedisTemplate> provider;


    public void check(String email) {
        Long attempts;
        try {
            StringRedisTemplate redis = provider.getIfAvailable();
            if (redis == null) throw new IllegalStateException();
            attempts = redis.execute(ATTEMPT, List.of("blogweb:auth:login:" + email));
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_STORE_UNAVAILABLE", "Authentication storage is unavailable");
        }
        if (attempts == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_STORE_UNAVAILABLE", "Authentication storage is unavailable");
        }
        if (attempts > 10) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "LOGIN_TOO_FREQUENT", "Too many login attempts; please try again later");
        }
    }
}
