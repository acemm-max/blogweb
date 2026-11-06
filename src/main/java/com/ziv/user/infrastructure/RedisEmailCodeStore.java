package com.ziv.user.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import com.ziv.constant.EmailConstant;
import com.ziv.user.dto.EmailCodePurpose;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisEmailCodeStore implements EmailCodeStore {
    // Reserve cooldown and code atomically so concurrent requests cannot both send.
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', %d)
            redis.call('SET', KEYS[2], ARGV[2], 'EX', %d)
            redis.call('SET', KEYS[4], ARGV[1], 'EX', %d)
            redis.call('DEL', KEYS[3])
            return 1
            """.formatted(EmailConstant.RESEND_INTERVAL_SECONDS, EmailConstant.CODE_TTL_SECONDS, EmailConstant.CODE_TTL_SECONDS), Long.class);

    // Keep the cooldown on failure. Never delete a newer request's code.
    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[4]) == ARGV[1] then
                return redis.call('DEL', KEYS[2], KEYS[3], KEYS[4])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> IS_CURRENT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[4]) == ARGV[1] and redis.call('EXISTS', KEYS[2]) == 1 then
                return 1
            end
            return 0
            """, Long.class);

    @Autowired
    private ObjectProvider<StringRedisTemplate> redisProvider;

    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            local code = redis.call('GET', KEYS[2])
            if not code then return 0 end
            if code == ARGV[1] then
                redis.call('DEL', KEYS[2], KEYS[3], KEYS[4])
                return 1
            end
            local attempts = redis.call('INCR', KEYS[3])
            if attempts == 1 then
                redis.call('PEXPIRE', KEYS[3], redis.call('PTTL', KEYS[2]))
            end
            if attempts >= %d then redis.call('DEL', KEYS[2], KEYS[3], KEYS[4]) end
            return 0
            """.formatted(EmailConstant.MAX_CODE_ATTEMPTS), Long.class);


    @Override
    public boolean reserve(String email, String code, String reservationId) {
        return reserve(email, code, reservationId, EmailCodePurpose.REGISTER);
    }

    @Override
    public void invalidate(String email, String reservationId) {
        invalidate(email, reservationId, EmailCodePurpose.REGISTER);
    }

    @Override
    public boolean consume(String email, String code) {
        return consume(email, code, EmailCodePurpose.REGISTER);
    }

    @Override
    public boolean reserve(String email, String code, String reservationId, EmailCodePurpose purpose) {
        return Long.valueOf(1).equals(redis().execute(RESERVE, keys(email, purpose), reservationId, code));
    }

    @Override
    public void invalidate(String email, String reservationId, EmailCodePurpose purpose) {
        redis().execute(INVALIDATE, keys(email, purpose), reservationId);
    }

    @Override
    public boolean consume(String email, String code, EmailCodePurpose purpose) {
        return Long.valueOf(1).equals(redis().execute(CONSUME, keys(email, purpose), code));
    }

    @Override
    public boolean isCurrent(String email, String reservationId, EmailCodePurpose purpose) {
        return Long.valueOf(1).equals(redis().execute(IS_CURRENT, keys(email, purpose), reservationId));
    }

    private StringRedisTemplate redis() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new IllegalStateException("Redis is not configured");
        }
        return redis;
    }

    private List<String> keys(String email, EmailCodePurpose purpose) {
        String prefix = EmailConstant.REDIS_KEY_PREFIX + "{" + email + "}:";
        String purposePrefix = purpose == EmailCodePurpose.LOGIN ? prefix + "login:" : prefix;
        return List.of(prefix + "cooldown", purposePrefix + "code", purposePrefix + "attempts", purposePrefix + "reservation");
    }
}
