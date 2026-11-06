package com.ziv.user.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginRequest {
    @NotBlank @Size(max = 254)
    private String email;
    @NotBlank @Size(max = 255)
    private String password;

    public LoginRequest(String email, String password) {
        setEmail(email);
        this.password = password;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
