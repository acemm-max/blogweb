package com.blogweb.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import com.blogweb.common.validation.AccountEmail;

public record SendEmailCodeRequest(
        @NotBlank @AccountEmail @Size(max = 254)
        String email, EmailCodePurpose purpose) {

    public SendEmailCodeRequest {
        if (purpose == null) purpose = EmailCodePurpose.REGISTER;
        if (email != null) {
            email = email.strip().toLowerCase(Locale.ROOT);
        }
    }
}
