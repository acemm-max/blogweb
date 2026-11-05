package com.blogweb.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blogweb.common.constant.EmailConstant;
import com.blogweb.common.exception.BusinessException;
import com.blogweb.common.util.EmailUtil;
import com.blogweb.user.dto.LoginRequest;
import com.blogweb.user.dto.LoginByCodeRequest;
import com.blogweb.user.dto.EmailCodePurpose;
import com.blogweb.user.dto.RegisterRequest;
import com.blogweb.user.dto.UserResponse;
import com.blogweb.user.entity.User;
import com.blogweb.user.infrastructure.EmailCodeStore;
import com.blogweb.user.infrastructure.LoginRateLimiter;
import com.blogweb.user.mapper.UserMapper;
import com.blogweb.user.security.UserSessionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import org.apache.commons.mail2.core.EmailException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private ObjectProvider<UserMapper> users;
    @Autowired
    private EmailCodeStore codes;
    @Autowired
    private LoginRateLimiter limiter;
    @Autowired
    private EmailUtil emailUtil;
    @Autowired
    private UserSessionManager sessions;
    private final SecureRandom random = new SecureRandom();


    public UserResponse register(RegisterRequest request) {
        if (findByEmail(request.email()) != null) throw duplicateEmail();
        boolean valid;
        try {
            valid = codes.consume(request.email(), request.code());
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_STORE_UNAVAILABLE", "Verification code storage is unavailable");
        }
        if (!valid) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "INVALID_EMAIL_CODE", "Verification code is invalid or expired");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(request.password());
        user.setNickname(request.nickname());
        user.setRole("USER");
        user.setStatus(1);
        try {
            mapper().insert(user);
            return UserResponse.from(user);
        } catch (DuplicateKeyException exception) {
            throw duplicateEmail();
        }
    }

    public UserResponse login(LoginRequest request) {
        return loginByPassword(request);
    }

    public UserResponse loginByPassword(LoginRequest request) {
        limiter.check(request.email());
        User user = findByEmail(request.email());
        boolean matches = user != null && user.getPassword() != null
                && MessageDigest.isEqual(request.password().getBytes(StandardCharsets.UTF_8),
                        user.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!matches || user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        return UserResponse.from(user);
    }

    public UserResponse loginByCode(LoginByCodeRequest request) {
        limiter.check(request.email());
        User user = findByEmail(request.email());
        boolean valid;
        try {
            valid = codes.consume(request.email(), request.code(), EmailCodePurpose.LOGIN);
        } catch (RuntimeException exception) {
            throw unavailable("EMAIL_STORE_UNAVAILABLE", "Verification code storage is unavailable");
        }
        if (!valid || user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "INVALID_LOGIN_CODE", "Email or verification code is invalid");
        }
        return UserResponse.from(user);
    }

    public UserResponse currentUser(String principal) {
        long id;
        try {
            id = Long.parseLong(principal);
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
        User user = mapper().selectById(id);
        if (user == null) throw unauthenticated();
        if (!Integer.valueOf(1).equals(user.getStatus())) throw unauthenticated();
        return UserResponse.from(user);
    }

    private User findByEmail(String email) {
        return mapper().selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
    }

    private UserMapper mapper() {
        UserMapper mapper = users.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Database is not configured; use the local profile");
        }
        return mapper;
    }

    private BusinessException duplicateEmail() {
        return new BusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    private BusinessException unauthenticated() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }
    public void sendEmailCode(String email) {
        sendEmailCode(email, EmailCodePurpose.REGISTER);
    }

    public void sendEmailCode(String email, EmailCodePurpose purpose) {
        emailUtil.validateConfiguration();
        String code = String.format(Locale.ROOT, EmailConstant.CODE_FORMAT,
                random.nextInt(EmailConstant.CODE_BOUND));
        String reservationId = UUID.randomUUID().toString();
        boolean reserved;
        try {
            reserved = purpose == EmailCodePurpose.REGISTER ? codes.reserve(email, code, reservationId)
                    : codes.reserve(email, code, reservationId, purpose);
        } catch (RuntimeException exception) {
            throw unavailable("EMAIL_STORE_UNAVAILABLE", "Verification code storage is unavailable");
        }
        if (!reserved) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "EMAIL_CODE_TOO_FREQUENT", "Please wait " + EmailConstant.RESEND_INTERVAL_SECONDS + " seconds before requesting another code");
        }
        try {
            emailUtil.sendCode(email, code);
        } catch (EmailException exception) {
            try {
                if (purpose == EmailCodePurpose.REGISTER) codes.invalidate(email, reservationId);
                else codes.invalidate(email, reservationId, purpose);
            } catch (RuntimeException ignored) {
                // Redis TTL still bounds the lifetime if cleanup is unavailable.
            }
            throw unavailable("EMAIL_SEND_FAILED", "Unable to send email; please try again later");
        }
    }

    private BusinessException unavailable(String code, String message) {
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
