package com.ziv.user.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "邮箱不能为空") @Size(max = 254, message = "邮箱不能超过254个字符")
    private String email;
    @NotBlank(message = "密码不能为空") @Size(min = 8, max = 255, message = "密码长度必须为8到255个字符")
    private String password;
    @NotBlank(message = "昵称不能为空") @Size(max = 64, message = "昵称不能超过64个字符")
    private String nickname;
    @NotBlank(message = "验证码不能为空") @Pattern(regexp = "[0-9]{6}", message = "验证码必须为6位数字")
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
