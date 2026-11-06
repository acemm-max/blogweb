package com.ziv.user.service;

import com.ziv.user.service.impl.UserServiceImpl;
import com.ziv.user.infrastructure.EmailCodeStore;
import com.ziv.user.infrastructure.EmailCodeDispatcher;
import com.ziv.user.dto.EmailCodePurpose;
import java.util.concurrent.RejectedExecutionException;

import com.ziv.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ziv.util.EmailUtil;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceTests {
    private final EmailCodeStore store = mock(EmailCodeStore.class);
    private final EmailUtil sender = mock(EmailUtil.class);
    private final EmailCodeDispatcher dispatcher = mock(EmailCodeDispatcher.class);
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "codes", store);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "emailUtil", sender);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "emailDispatcher", dispatcher);
    }

    @Test
    void sendsSameSixDigitCodeAsStored() throws Exception {
        when(store.reserve(anyString(), anyString(), anyString())).thenReturn(true);
        service.sendEmailCode("123456@qq.com");
        var code = ArgumentCaptor.forClass(String.class);
        verify(store).reserve(eq("123456@qq.com"), code.capture(), anyString());
        assertThat(code.getValue()).matches("[0-9]{6}");
        verify(dispatcher).submit(eq("123456@qq.com"), eq(code.getValue()), anyString(), eq(EmailCodePurpose.REGISTER));
        verify(sender, never()).sendCode(anyString(), anyString());
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
    void queueRejectionInvalidatesReservation() throws Exception {
        when(store.reserve(anyString(), anyString(), anyString())).thenReturn(true);
        doThrow(new RejectedExecutionException()).when(dispatcher)
                .submit(anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> service.sendEmailCode("123456@qq.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(503));
        var reservation = ArgumentCaptor.forClass(String.class);
        verify(store).reserve(eq("123456@qq.com"), anyString(), reservation.capture());
        verify(store).invalidate("123456@qq.com", reservation.getValue());
    }

    @Test
    void missingCredentialsDoesNotTouchRedisOrSend() {
        doThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 503, "Not configured"))
                .when(sender).validateConfiguration();
        assertThatThrownBy(() -> service.sendEmailCode("123456@qq.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(503));
        verifyNoInteractions(store, dispatcher);
    }

    @Test
    void redisFailureDoesNotSend() throws Exception {
        when(store.reserve(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException());
        assertThatThrownBy(() -> service.sendEmailCode("123456@qq.com"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(503));
        verify(sender, never()).sendCode(anyString(), anyString());
    }
}
