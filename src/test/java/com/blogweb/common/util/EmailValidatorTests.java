package com.blogweb.common.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmailValidatorTests {
    @ParameterizedTest
    @ValueSource(strings = {"123456@qq.com", "user@example.com", "USER@EXAMPLE.COM",
            "first.last+blog@mail.example.com", "user@my-domain.com"})
    void acceptsComEmail(String email) {
        assertThat(EmailValidator.isValidEmail(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "user", "@qq.com", "user@.com", "user@com", "user@qq.net",
            "user@qq.com.cn", "user@qq.com.evil", "user@@qq.com", "user name@qq.com",
            "user@-qq.com", ".user@qq.com", "user..name@qq.com", "user@qq.com\n"})
    void rejectsMalformedEmail(String email) {
        assertThat(EmailValidator.isValidEmail(email)).isFalse();
    }

    @Test
    void rejectsNullAndOversizedLocalPart() {
        assertThat(EmailValidator.isValidEmail(null)).isFalse();
        assertThat(EmailValidator.isValidEmail("a".repeat(65) + "@qq.com")).isFalse();
    }
}
