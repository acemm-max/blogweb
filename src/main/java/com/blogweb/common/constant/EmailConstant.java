package com.blogweb.common.constant;

public final class EmailConstant {
    public static final int MAIL_THREAD_COUNT = 5;
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
