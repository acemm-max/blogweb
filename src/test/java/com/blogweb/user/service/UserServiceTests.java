package com.blogweb.user.service;

import com.blogweb.user.infrastructure.EmailCodeStore;

import com.blogweb.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.blogweb.common.util.EmailUtil;
import org.apache.commons.mail2.core.EmailException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceTests {
    private final EmailCodeStore store = mock(EmailCodeStore.class);
    private final EmailUtil sender = mock(EmailUtil.class);
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codes", store);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "emailUtil", sender);
    }

    @Test
    void sendsSameSixDigitCodeAsStored() throws Exception {
        when(store.reserve(anyString(), anyString(), anyString())).thenReturn(true);
        service.sendEmailCode("123456@qq.com");
        var code = ArgumentCaptor.forClass(String.class);
        verify(store).reserve(eq("123456@qq.com"), code.capture(), anyString());
        assertThat(code.getValue()).matches("[0-9]{6}");
        verify(sender).sendCode("123456@qq.com", code.getValue());
    }

    @Test
    void throttledRequestDoesNotSend() throws Exception {
        when(store.reserve(anyString(), anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.sendEmailCode("123456@qq.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(429));
        verify(sender, never()).sendCode(anyString(), anyString());
    }

    @Test
    void mailFailureInvalidatesReservation() throws Exception {
        when(store.reserve(anyString(), anyString(), anyString())).thenReturn(true);
        doThrow(new EmailException("SMTP unavailable")).when(sender).sendCode(anyString(), anyString());
        assertThatThrownBy(() -> service.sendEmailCode("123456@qq.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("EMAIL_SEND_FAILED"));
        var reservation = ArgumentCaptor.forClass(String.class);
        verify(store).reserve(eq("123456@qq.com"), anyString(), reservation.capture());
        verify(store).invalidate("123456@qq.com", reservation.getValue());
    }

    @Test
    void missingCredentialsDoesNotTouchRedisOrSend() {
        doThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED", "Not configured"))
                .when(sender).validateConfiguration();
        assertThatThrownBy(() -> service.sendEmailCode("123456@qq.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("EMAIL_NOT_CONFIGURED"));
        verifyNoInteractions(store);
    }

    @Test
    void redisFailureDoesNotSend() throws Exception {
        when(store.reserve(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException());
        assertThatThrownBy(() -> service.sendEmailCode("123456@qq.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("EMAIL_STORE_UNAVAILABLE"));
        verify(sender, never()).sendCode(anyString(), anyString());
    }
}
