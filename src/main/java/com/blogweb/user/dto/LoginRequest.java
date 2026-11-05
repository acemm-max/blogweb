package com.blogweb.user.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;
import com.blogweb.common.validation.AccountEmail;

public record LoginRequest(
        @NotBlank @AccountEmail @Size(max = 254) String email,
        @NotBlank @Size(max = 255) String password) {
    public LoginRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
