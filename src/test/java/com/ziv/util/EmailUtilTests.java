package com.ziv.util;

import com.ziv.constant.EmailConstant;
import com.ziv.exception.BusinessException;
import org.apache.commons.mail.SimpleEmail;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmailUtilTests {
    @Test
    void createsSimpleEmailUsingConstantsAndMessage() throws Exception {
        EmailUtil util = new EmailUtil();
        SimpleEmail configured = util.createEmail();
        assertThat(configured.getMailSession().getProperty("mail.smtp.host")).isEqualTo(EmailConstant.SMTP_HOST);
        assertThat(configured.getMailSession().getProperty("mail.smtp.port")).isEqualTo(Integer.toString(EmailConstant.SMTP_PORT));
        assertThat(configured.getMailSession().getProperty("mail.smtp.writetimeout"))
                .isEqualTo(Integer.toString(EmailConstant.SMTP_WRITE_TIMEOUT_MS));
        assertThat(configured.getMailSession().getProperty("mail.smtp.ssl.enable"))
                .isEqualTo(Boolean.toString(EmailConstant.SMTP_SSL_ENABLED));
        SimpleEmail email = mock(SimpleEmail.class);
        EmailUtil sender = spy(new EmailUtil());
        doNothing().when(sender).validateConfiguration();
        doReturn(email).when(sender).createEmail();
        sender.sendCode("recipient@qq.com", "012345");
        verify(email).setFrom(EmailConstant.SMTP_USERNAME, EmailConstant.SMTP_NICKNAME);
        verify(email).addTo("recipient@qq.com");
        verify(email).setSubject(EmailConstant.SUBJECT);
        verify(email).setMsg(EmailConstant.BODY.formatted("012345", EmailConstant.CODE_TTL_SECONDS / 60));
        verify(email).send();
    }

    @Test
    void missingConfigurationIsRejected() {
        assumeTrue(!EmailConstant.EMAIL_ENABLED || EmailConstant.SMTP_USERNAME.isBlank()
                || EmailConstant.SMTP_PASSWORD.isBlank());
        assertThatThrownBy(new EmailUtil()::validateConfiguration)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(503));
    }
}
