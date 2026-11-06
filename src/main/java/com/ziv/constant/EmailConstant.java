package com.ziv.constant;

public final class EmailConstant {
    public static final boolean EMAIL_ENABLED = false;
    public static final String SMTP_HOST = "smtp.qq.com";
    public static final int SMTP_PORT = 465;
    // Fill in the sender QQ email address and SMTP authorization code here.
    public static final String SMTP_USERNAME = "1537421491@qq.com";
    public static final String SMTP_PASSWORD = "rjxyxtcqshqnbadg";
    public static final String CHARSET = "UTF-8";
    public static final boolean SMTP_AUTH = true;
    public static final boolean SMTP_SSL_ENABLED = true;
    public static final boolean SMTP_CHECK_SERVER_IDENTITY = true;
    public static final int SMTP_CONNECTION_TIMEOUT_MS = 5000;
    public static final int SMTP_READ_TIMEOUT_MS = 5000;
    public static final int SMTP_WRITE_TIMEOUT_MS = 5000;
    public static final int MAIL_THREAD_COUNT = 5;
    public static final int MAIL_QUEUE_CAPACITY = 100;
    public static final String MAIL_THREAD_PREFIX = "blogweb-email-";
    public static final int MAIL_SHUTDOWN_WAIT_SECONDS = 30;
    public static final int CODE_BOUND = 1_000_000;
    public static final String CODE_FORMAT = "%06d";
    public static final int CODE_TTL_SECONDS = 300;
    public static final int RESEND_INTERVAL_SECONDS = 60;
    public static final int MAX_CODE_ATTEMPTS = 5;
    public static final String REDIS_KEY_PREFIX = "blogweb:auth:email:";
    public static final String SUBJECT = "Blogweb 邮箱验证码";
    public static final String BODY = "您的 Blogweb 邮箱验证码为：%s\n验证码 %d 分钟内有效，请勿向他人透露。如非本人操作，请忽略此邮件。";

    private EmailConstant() {
    }
}
