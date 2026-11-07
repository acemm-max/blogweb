package com.ziv.user.service.impl;

import com.ziv.user.service.UserService;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ziv.constant.EmailConstant;
import com.ziv.exception.BusinessException;
import com.ziv.util.EmailUtil;
import com.ziv.user.dto.LoginRequest;
import com.ziv.user.dto.LoginByCodeRequest;
import com.ziv.user.dto.EmailCodePurpose;
import com.ziv.user.dto.RegisterRequest;
import com.ziv.user.dto.UserResponse;
import com.ziv.user.entity.User;
import com.ziv.user.infrastructure.EmailCodeStore;
import com.ziv.user.infrastructure.EmailCodeDispatcher;
import com.ziv.exception.ErrorCode;
import com.ziv.user.infrastructure.LoginRateLimiter;
import com.ziv.user.mapper.UserMapper;
import com.ziv.user.security.UserSessionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserServiceImpl.class);
    private static final Pattern ACCOUNT_EMAIL_PATTERN = Pattern.compile(
            "^(?=.{1,64}@)[A-Z0-9_%+-]+(?:\\.[A-Z0-9_%+-]+)*@"
                    + "(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+COM$",
            Pattern.CASE_INSENSITIVE);

    @Autowired
    private ObjectProvider<UserMapper> users;
    @Autowired
    private EmailCodeStore codes;
    @Autowired
    private LoginRateLimiter limiter;
    @Autowired
    private EmailUtil emailUtil;
    @Autowired
    private EmailCodeDispatcher emailDispatcher;
    @Autowired
    private UserSessionManager sessions;
    private final SecureRandom random = new SecureRandom();


    public UserResponse register(RegisterRequest request) {
        validateEmail(request.getEmail());
        if (findByEmail(request.getEmail()) != null) throw duplicateEmail();
        boolean valid;
        try {
            valid = codes.consume(request.getEmail(), request.getCode());
        } catch (RuntimeException exception) {
            com.ziv.util.ErrorLogUtil.log(log, "Verification code storage operation failed", exception);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "Verification code storage is unavailable");
        }
        if (!valid) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    ErrorCode.BAD_REQUEST.getCode(), "Verification code is invalid or expired");
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setNickname(request.getNickname());
        user.setRole("USER");
        user.setStatus(1);
        try {
            mapper().insert(user);
            return UserResponse.from(user);
        } catch (DuplicateKeyException exception) {
            com.ziv.util.ErrorLogUtil.log(log, "Registration insert conflicted with an existing key", exception);
            throw duplicateEmail();
        }
    }

    public UserResponse login(LoginRequest request) {
        return loginByPassword(request);
    }

    public UserResponse loginByPassword(LoginRequest request) {
        validateEmail(request.getEmail());
        limiter.check(request.getEmail());
        User user = findByEmail(request.getEmail());
        boolean matches = user != null && user.getPassword() != null
                && MessageDigest.isEqual(request.getPassword().getBytes(StandardCharsets.UTF_8),
                        user.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!matches || user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED.getCode(), "Email or password is incorrect");
        }
        return UserResponse.from(user);
    }

    public UserResponse loginByCode(LoginByCodeRequest request) {
        validateEmail(request.getEmail());
        limiter.check(request.getEmail());
        User user = findByEmail(request.getEmail());
        boolean valid;
        try {
            valid = codes.consume(request.getEmail(), request.getCode(), EmailCodePurpose.LOGIN);
        } catch (RuntimeException exception) {
            com.ziv.util.ErrorLogUtil.log(log, "Verification code storage operation failed", exception);
            throw unavailable(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "Verification code storage is unavailable");
        }
        if (!valid || user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED.getCode(), "Email or verification code is invalid");
        }
        return UserResponse.from(user);
    }

    public UserResponse currentUser(String principal) {
        long id;
        try {
            id = Long.parseLong(principal);
        } catch (NumberFormatException exception) {
            log.warn("Invalid user principal: type={}", exception.getClass().getSimpleName());
            throw unauthenticated();
        }
        User user = mapper().selectById(id);
        if (user == null) throw unauthenticated();
        if (!Integer.valueOf(1).equals(user.getStatus())) throw unauthenticated();
        return UserResponse.from(user);
    }

    private void validateEmail(String email) {
        if (email == null || email.length() > 254 || !ACCOUNT_EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getCode(),
                    "邮箱格式必须为账号@域名.com");
        }
    }

    private User findByEmail(String email) {
        return mapper().selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    }

    private UserMapper mapper() {
        UserMapper mapper = users.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "Database is not configured; use the local profile");
        }
        return mapper;
    }

    private BusinessException duplicateEmail() {
        return new BusinessException(HttpStatus.CONFLICT, ErrorCode.CONFLICT.getCode(), "Email is already registered");
    }

    private BusinessException unauthenticated() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), "Authentication required");
    }
    public void sendEmailCode(String email) {
        sendEmailCode(email, EmailCodePurpose.REGISTER);
    }

    public void sendEmailCode(String email, EmailCodePurpose purpose) {
        validateEmail(email);
        emailUtil.validateConfiguration();
        String code = String.format(Locale.ROOT, EmailConstant.CODE_FORMAT,
                random.nextInt(EmailConstant.CODE_BOUND));
        String reservationId = UUID.randomUUID().toString();
        boolean reserved;
        try {
            reserved = purpose == EmailCodePurpose.REGISTER ? codes.reserve(email, code, reservationId)
                    : codes.reserve(email, code, reservationId, purpose);
        } catch (RuntimeException exception) {
            com.ziv.util.ErrorLogUtil.log(log, "Verification code storage operation failed", exception);
            throw unavailable(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "Verification code storage is unavailable");
        }
        if (!reserved) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.TOO_MANY_REQUESTS.getCode(), "Please wait " + EmailConstant.RESEND_INTERVAL_SECONDS + " seconds before requesting another code");
        }
        try {
            emailDispatcher.submit(email, code, reservationId, purpose);
        } catch (RejectedExecutionException exception) {
            com.ziv.util.ErrorLogUtil.log(log, "Email executor rejected a task", exception);
            try {
                if (purpose == EmailCodePurpose.REGISTER) codes.invalidate(email, reservationId);
                else codes.invalidate(email, reservationId, purpose);
            } catch (RuntimeException cleanupFailure) {
                com.ziv.util.ErrorLogUtil.log(log, "Email reservation cleanup failed; Redis TTL remains active", cleanupFailure);
                // Redis TTL still bounds the lifetime if cleanup is unavailable.
            }
            throw unavailable(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "Email queue is full; please try again later");
        }
    }

    private BusinessException unavailable(int code, String message) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
    public UserResponse createSession(LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserResponse user = loginByPassword(body);
        sessions.create(user, request, response);
        return user;
    }

    public UserResponse createCodeSession(LoginByCodeRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserResponse user = loginByCode(body);
        sessions.create(user, request, response);
        return user;
    }

    public void deleteSession(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        sessions.delete(request, response, authentication);
    }
}
