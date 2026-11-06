package com.ziv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginByCodeRequest {
    @NotBlank @Size(max = 254)
    private String email;
    @NotBlank @Pattern(regexp = "[0-9]{6}")
    private String code;

    public LoginByCodeRequest(String email, String code) {
        setEmail(email);
        this.code = code;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginByCodeRequest[credentials=REDACTED]";
    }
}
