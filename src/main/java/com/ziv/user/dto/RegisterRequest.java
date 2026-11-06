package com.ziv.user.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank @Size(max = 254)
    private String email;
    @NotBlank @Size(min = 8, max = 255)
    private String password;
    @NotBlank @Size(max = 64)
    private String nickname;
    @NotBlank @Pattern(regexp = "[0-9]{6}")
    private String code;

    public RegisterRequest(String email, String password, String nickname, String code) {
        setEmail(email);
        this.password = password;
        setNickname(nickname);
        this.code = code;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public void setNickname(String nickname) {
        this.nickname = nickname == null ? null : nickname.strip();
    }

    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}
