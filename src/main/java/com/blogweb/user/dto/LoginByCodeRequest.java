package com.blogweb.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import com.blogweb.common.validation.AccountEmail;

public record LoginByCodeRequest(
        @NotBlank @AccountEmail @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    public LoginByCodeRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginByCodeRequest[credentials=REDACTED]";
    }
}
