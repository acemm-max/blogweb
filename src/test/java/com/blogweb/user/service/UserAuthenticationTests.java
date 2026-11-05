package com.blogweb.user.service;
import com.blogweb.user.infrastructure.*;

import com.blogweb.user.dto.*;
import com.blogweb.common.exception.BusinessException;
import com.blogweb.user.entity.User;
import com.blogweb.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAuthenticationTests {
    private final UserMapper users = mock(UserMapper.class);
    private final EmailCodeStore codes = mock(EmailCodeStore.class);
    private final LoginRateLimiter limiter = mock(LoginRateLimiter.class);
    private UserService service;
    private final RegisterRequest request = new RegisterRequest("123456@qq.com", "TestPassword123!", "Tester", "012345");

    @BeforeEach
    void setUp() {
        service = new UserService();
        var factory = new org.springframework.beans.factory.support.StaticListableBeanFactory(
                java.util.Map.of("userMapper", users));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "users", factory.getBeanProvider(UserMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codes", codes);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "limiter", limiter);
    }

    @Test
    void registrationStoresSubmittedPasswordAndCreatesOrdinaryEnabledUser() {
        when(codes.consume(request.email(), request.code())).thenReturn(true);
        when(users.insert(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            assertThat(user.getPassword()).isEqualTo(request.password());
            user.setId(1L);
            return 1;
        });
        UserResponse result = service.register(request);
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.status()).isEqualTo(1);
    }

    @Test
    void invalidCodeNeverCreatesUser() {
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_EMAIL_CODE"));
        verify(users, never()).insert(any(User.class));
    }

    @Test
    void concurrentDuplicateInsertReturnsConflict() {
        when(codes.consume(anyString(), anyString())).thenReturn(true);
        when(users.insert(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(409));
    }

    @Test
    void submittedValueOver72BytesIsStoredUnchanged() {
        var longRequest = new RegisterRequest(request.email(), "密".repeat(25), "Tester", "012345");
        when(codes.consume(anyString(), anyString())).thenReturn(true);
        when(users.insert(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            assertThat(user.getPassword()).isEqualTo(longRequest.password());
            user.setId(1L);
            return 1;
        });
        assertThat(service.register(longRequest).id()).isEqualTo(1L);
    }

    @Test
    void validCredentialsCanLogin() {
        when(users.selectOne(any(Wrapper.class))).thenReturn(user(1));
        assertThat(service.login(new LoginRequest(request.email(), request.password())).id()).isEqualTo(1);
        verify(limiter).check(request.email());
    }

    @Test
    void wrongPasswordUnknownEmailAndDisabledUserHaveSameError() {
        LoginRequest login = new LoginRequest(request.email(), request.password());
        when(users.selectOne(any(Wrapper.class))).thenReturn(null)
                .thenReturn(user(0)).thenReturn(user(1));
        assertInvalidCredentials(login);
        assertInvalidCredentials(login);
        assertInvalidCredentials(new LoginRequest(request.email(), "wrong-password"));
    }

    @Test
    void disabledUserCannotFetchCurrentUser() {
        when(users.selectById(1L)).thenReturn(user(0));
        assertThatThrownBy(() -> service.currentUser("1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(401));
    }

    @Test
    void loginRateLimitStopsPasswordLookup() {
        doThrow(new BusinessException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "LOGIN_TOO_FREQUENT", "Wait"))
                .when(limiter).check(request.email());
        assertThatThrownBy(() -> service.login(new LoginRequest(request.email(), request.password())))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(users);
    }

    @Test
    void loginByCodeRequiresLoginPurposeAndConsumesCode() {
        when(users.selectOne(any(Wrapper.class))).thenReturn(user(1));
        when(codes.consume(request.email(), "012345", EmailCodePurpose.LOGIN)).thenReturn(true).thenReturn(false);
        var body = new LoginByCodeRequest(" 123456@QQ.COM ", "012345");
        assertThat(service.loginByCode(body).id()).isEqualTo(1L);
        assertThatThrownBy(() -> service.loginByCode(body)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_LOGIN_CODE"));
        verify(codes, never()).consume(anyString(), anyString());
    }

    @Test
    void loginByCodeRejectsUnknownAndDisabledAccounts() {
        when(users.selectOne(any(Wrapper.class))).thenReturn(null).thenReturn(user(0));
        when(codes.consume(request.email(), "012345", EmailCodePurpose.LOGIN)).thenReturn(true);
        var body = new LoginByCodeRequest(request.email(), "012345");
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.loginByCode(body)).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getStatus().value()).isEqualTo(401));
        }
        verify(users, never()).insert(any(User.class));
    }

    @Test
    void loginByCodeFailsClosedWhenRedisIsUnavailable() {
        when(users.selectOne(any(Wrapper.class))).thenReturn(user(1));
        when(codes.consume(anyString(), anyString(), eq(EmailCodePurpose.LOGIN)))
                .thenThrow(new IllegalStateException());
        assertThatThrownBy(() -> service.loginByCode(new LoginByCodeRequest(request.email(), "012345")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(503));
    }

    @Test
    void codeLoginSharesLoginRateLimit() {
        doThrow(new BusinessException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "LOGIN_TOO_FREQUENT", "Wait")).when(limiter).check(request.email());
        assertThatThrownBy(() -> service.loginByCode(new LoginByCodeRequest(request.email(), "012345")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(429));
        verifyNoInteractions(users, codes);
    }

    private void assertInvalidCredentials(LoginRequest login) {
        assertThatThrownBy(() -> service.login(login)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    private User user(int status) {
        User user = new User();
        user.setId(1L);
        user.setEmail(request.email());
        user.setPassword(request.password());
        user.setNickname("Tester");
        user.setRole("USER");
        user.setStatus(status);
        return user;
    }
}
