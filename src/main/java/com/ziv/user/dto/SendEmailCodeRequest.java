package com.ziv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SendEmailCodeRequest {
    @NotBlank @Size(max = 254)
    private String email;
    private EmailCodePurpose purpose = EmailCodePurpose.REGISTER;

    public SendEmailCodeRequest(String email, EmailCodePurpose purpose) {
        setEmail(email);
        setPurpose(purpose);
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public void setPurpose(EmailCodePurpose purpose) {
        this.purpose = purpose == null ? EmailCodePurpose.REGISTER : purpose;
    }
}
