package com.blogweb.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import com.blogweb.common.constant.EmailConstant;
import com.blogweb.common.exception.BusinessException;
import jakarta.mail.Session;
import java.util.Properties;
import org.apache.commons.mail2.core.EmailException;
import org.apache.commons.mail2.jakarta.DefaultAuthenticator;
import org.apache.commons.mail2.jakarta.SimpleEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Creates a separate mail object per send; all SMTP settings come from configuration. */
@Component
public class EmailUtil {
    @Autowired
    private MailProperties properties;
    @Value("${blogweb.email-code.enabled:false}")
    private boolean enabled;


    public void validateConfiguration() {
        if (!enabled || properties.getHost() == null || properties.getHost().isBlank()
                || properties.getPort() == null || properties.getUsername() == null
                || properties.getUsername().isBlank() || properties.getPassword() == null
                || properties.getPassword().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_NOT_CONFIGURED", "Email service is not configured");
        }
    }

    public void sendCode(String recipient, String code) throws EmailException {
        validateConfiguration();
        Properties settings = new Properties();
        settings.putAll(properties.getProperties());
        settings.setProperty("mail.smtp.host", properties.getHost());
        settings.setProperty("mail.smtp.port", properties.getPort().toString());
        SimpleEmail email = createEmail();
        email.setMailSession(Session.getInstance(settings,
                new DefaultAuthenticator(properties.getUsername(), properties.getPassword())));
        email.setCharset(properties.getDefaultEncoding().name());
        email.setFrom(properties.getUsername());
        email.addTo(recipient);
        email.setSubject(EmailConstant.SUBJECT);
        email.setMsg(EmailConstant.BODY.formatted(code, EmailConstant.CODE_TTL_SECONDS / 60));
        email.send();
    }

    protected SimpleEmail createEmail() {
        return new SimpleEmail();
    }
}
