package com.ziv.user.infrastructure;

import com.ziv.util.ErrorLogUtil;
import com.ziv.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import com.ziv.exception.BusinessException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LoginRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
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
            ErrorLogUtil.log(log, "Login rate limiter storage operation failed", exception);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "Authentication storage is unavailable");
        }
        if (attempts == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "Authentication storage is unavailable");
        }
        if (attempts > 10) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.TOO_MANY_REQUESTS.getCode(), "Too many login attempts; please try again later");
        }
    }
}
