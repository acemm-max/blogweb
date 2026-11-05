package com.blogweb.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = AccountEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AccountEmail {
    String message() default "邮箱格式必须为账号@域名.com";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
