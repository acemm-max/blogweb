package com.ziv.util;

import com.ziv.exception.ErrorCode;
import com.ziv.constant.EmailConstant;
import com.ziv.exception.BusinessException;
import javax.mail.Session;
import java.util.Properties;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.SimpleEmail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Creates a separate SimpleEmail per send using EmailConstant settings. */
@Component
public class EmailUtil {
    /**
     * 发件前检查配置
     */
    public void validateConfiguration() {
        if (!EmailConstant.EMAIL_ENABLED || EmailConstant.SMTP_HOST.isBlank()
                || EmailConstant.SMTP_PORT < 1 || EmailConstant.SMTP_PORT > 65535
                || EmailConstant.SMTP_USERNAME.isBlank() || EmailConstant.SMTP_PASSWORD.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SERVICE_UNAVAILABLE.getCode(), "Email service is not configured");
        }
    }

    public void sendCode(String recipient, String code) throws EmailException {
        validateConfiguration();
        SimpleEmail email = createEmail();
        email.setFrom(EmailConstant.SMTP_USERNAME,EmailConstant.SMTP_NICKNAME);
        email.addTo(recipient);
        email.setSubject(EmailConstant.SUBJECT);
        email.setMsg(EmailConstant.BODY.formatted(code, EmailConstant.CODE_TTL_SECONDS / 60));
        email.send();
    }

    protected SimpleEmail createEmail() {
        Properties settings = new Properties();
        settings.setProperty("mail.smtp.host", EmailConstant.SMTP_HOST);
        settings.setProperty("mail.smtp.port", Integer.toString(EmailConstant.SMTP_PORT));
        settings.setProperty("mail.smtp.auth", Boolean.toString(EmailConstant.SMTP_AUTH));
        settings.setProperty("mail.smtp.ssl.enable", Boolean.toString(EmailConstant.SMTP_SSL_ENABLED));
        settings.setProperty("mail.smtp.ssl.checkserveridentity", Boolean.toString(EmailConstant.SMTP_CHECK_SERVER_IDENTITY));
        settings.setProperty("mail.smtp.connectiontimeout", Integer.toString(EmailConstant.SMTP_CONNECTION_TIMEOUT_MS));
        settings.setProperty("mail.smtp.timeout", Integer.toString(EmailConstant.SMTP_READ_TIMEOUT_MS));
        settings.setProperty("mail.smtp.writetimeout", Integer.toString(EmailConstant.SMTP_WRITE_TIMEOUT_MS));
        SimpleEmail email = new SimpleEmail();
        email.setMailSession(Session.getInstance(settings,
                new DefaultAuthenticator(EmailConstant.SMTP_USERNAME, EmailConstant.SMTP_PASSWORD)));
        email.setCharset(EmailConstant.CHARSET);
        return email;
    }
}
