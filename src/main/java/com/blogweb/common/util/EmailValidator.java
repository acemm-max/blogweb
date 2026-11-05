package com.blogweb.common.util;

import java.util.regex.Pattern;

public final class EmailValidator {
    private static final Pattern ACCOUNT_EMAIL_PATTERN = Pattern.compile(
            "^(?=.{1,64}@)[A-Z0-9_%+-]+(?:\\.[A-Z0-9_%+-]+)*@"
                    + "(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+COM$",
            Pattern.CASE_INSENSITIVE);

    private EmailValidator() {
    }

    /** Validates the whole account email, including the required .com domain suffix. */
    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 254 && ACCOUNT_EMAIL_PATTERN.matcher(email).matches();
    }
}
