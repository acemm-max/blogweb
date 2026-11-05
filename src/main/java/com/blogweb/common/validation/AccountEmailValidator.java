package com.blogweb.common.validation;

import com.blogweb.common.util.EmailValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AccountEmailValidator implements ConstraintValidator<AccountEmail, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return EmailValidator.isValidEmail(value);
    }
}
