package com.blogweb.common.util;

import com.blogweb.common.exception.BusinessException;
import org.apache.commons.mail2.jakarta.SimpleEmail;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailUtilTests {
    @Test
    void createsSimpleEmailUsingConfiguredSmtpAndMessage() throws Exception {
        MailProperties properties = new MailProperties();
        properties.setHost("smtp.example.test");
        properties.setPort(2465);
        properties.setUsername("sender@example.test");
        properties.setPassword("test-only");
        properties.getProperties().put("mail.smtp.ssl.enable", "true");
        properties.getProperties().put("mail.smtp.writetimeout", "4321");
        SimpleEmail email = spy(new SimpleEmail());
        doReturn("test-message-id").when(email).send();
        EmailUtil util = new EmailUtil() {
            @Override
            protected SimpleEmail createEmail() {
                return email;
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(util, "properties", properties);
        org.springframework.test.util.ReflectionTestUtils.setField(util, "enabled", true);
        util.sendCode("recipient@qq.com", "012345");
        email.buildMimeMessage();
        assertThat(email.getMailSession().getProperty("mail.smtp.host")).isEqualTo("smtp.example.test");
        assertThat(email.getMailSession().getProperty("mail.smtp.port")).isEqualTo("2465");
        assertThat(email.getMailSession().getProperty("mail.smtp.writetimeout")).isEqualTo("4321");
        assertThat(email.getMimeMessage().getFrom()[0].toString()).isEqualTo("sender@example.test");
        assertThat(email.getMimeMessage().getAllRecipients()[0].toString()).isEqualTo("recipient@qq.com");
        assertThat(email.getMimeMessage().getContent().toString()).contains("012345", "5 分钟");
        verify(email).send();
    }

    @Test
    void missingConfigurationIsRejected() {
        EmailUtil util = new EmailUtil();
        org.springframework.test.util.ReflectionTestUtils.setField(util, "properties", new MailProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(util, "enabled", true);
        assertThatThrownBy(util::validateConfiguration).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo("EMAIL_NOT_CONFIGURED"));
    }
}
