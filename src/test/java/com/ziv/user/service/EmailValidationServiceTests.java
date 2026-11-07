package com.ziv.user.service;

import com.ziv.user.service.impl.UserServiceImpl;
import com.ziv.user.infrastructure.EmailCodeStore;
import com.ziv.user.infrastructure.EmailCodeDispatcher;
import com.ziv.util.EmailUtil;
import com.ziv.exception.BusinessException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmailValidationServiceTests {
    private final UserServiceImpl service = new UserServiceImpl();
    private final EmailCodeStore codes = mock(EmailCodeStore.class);
    private final EmailUtil mail = mock(EmailUtil.class);
    private final EmailCodeDispatcher dispatcher = mock(EmailCodeDispatcher.class);

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "codes", codes);
        ReflectionTestUtils.setField(service, "emailUtil", mail);
        ReflectionTestUtils.setField(service, "emailDispatcher", dispatcher);
        when(codes.reserve(anyString(), anyString(), anyString())).thenReturn(true);
    }

    private void assertInvalid(String email) {
        assertThatThrownBy(() -> service.sendEmailCode(email)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(400));
        verifyNoInteractions(mail, dispatcher);
        verify(codes, never()).reserve(anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456@qq.com", "user@example.com", "USER@EXAMPLE.COM",
            "first.last+blog@mail.example.com", "user@my-domain.com"})
    void acceptsComEmail(String email) {
        service.sendEmailCode(email);
        verify(dispatcher).submit(eq(email), anyString(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "user", "@qq.com", "user@.com", "user@com", "user@qq.net",
            "user@qq.com.cn", "user@qq.com.evil", "user@@qq.com", "user name@qq.com",
            "user@-qq.com", ".user@qq.com", "user..name@qq.com", "user@qq.com\n"})
    void rejectsMalformedEmail(String email) {
        assertInvalid(email);
    }

    @Test
    void rejectsNullAndOversizedLocalPart() {
        assertInvalid(null);
        assertInvalid("a".repeat(65) + "@qq.com");
    }
}
