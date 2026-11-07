package com.ziv.user.infrastructure;

import com.ziv.util.EmailUtil;
import com.ziv.user.dto.EmailCodePurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class EmailCodeDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EmailCodeDispatcher.class);
    @Autowired
    @Qualifier("emailExecutor")
    private ThreadPoolTaskExecutor executor;
    @Autowired
    private EmailCodeStore codes;
    @Autowired
    private EmailUtil emailUtil;

    public void submit(String email, String code, String reservationId, EmailCodePurpose purpose) {
        executor.execute(() -> send(email, code, reservationId, purpose));
    }

    private void send(String email, String code, String reservationId, EmailCodePurpose purpose) {
        try {
            // Skip stale, consumed or superseded codes after waiting in the queue.
            if (!codes.isCurrent(email, reservationId, purpose)) return;
            emailUtil.sendCode(email, code);
        } catch (Exception exception) {
            try {
                codes.invalidate(email, reservationId, purpose);
            } catch (RuntimeException cleanupFailure) {
                com.ziv.util.ErrorLogUtil.log(log, "Email code cleanup failed; Redis TTL remains active", cleanupFailure);
            }
            // Never log the credential, authorization code or SMTP exception text.
            com.ziv.util.ErrorLogUtil.log(log, "Verification email failed: purpose=" + purpose, exception);
        }
    }
}
