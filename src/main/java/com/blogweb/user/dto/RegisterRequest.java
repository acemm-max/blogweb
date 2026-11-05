package com.blogweb.user.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;
import com.blogweb.common.validation.AccountEmail;

public record RegisterRequest(
        @NotBlank @AccountEmail @Size(max = 254)
        String email,
        @NotBlank @Size(min = 8, max = 255) String password,
        @NotBlank @Size(max = 64) String nickname,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    public RegisterRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
        if (nickname != null) nickname = nickname.strip();
    }

    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}
