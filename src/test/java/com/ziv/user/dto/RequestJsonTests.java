package com.ziv.user.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestJsonTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void normalizesRequestFieldsDuringJsonDeserialization() throws Exception {
        RegisterRequest registration = mapper.readValue("""
                {"email":" User@QQ.COM ","password":" password123 ","nickname":" Tester ","code":"012345"}
                """, RegisterRequest.class);
        assertThat(registration.getEmail()).isEqualTo("user@qq.com");
        assertThat(registration.getNickname()).isEqualTo("Tester");
        assertThat(registration.getPassword()).isEqualTo(" password123 ");
        assertThat(registration.getCode()).isEqualTo("012345");

        LoginByPasswordRequest passwordLogin = mapper.readValue("""
                {"email":" User@QQ.COM ","password":" password123 "}
                """, LoginByPasswordRequest.class);
        assertThat(passwordLogin.getEmail()).isEqualTo("user@qq.com");
        assertThat(passwordLogin.getPassword()).isEqualTo(" password123 ");

        LoginByCodeRequest codeLogin = mapper.readValue("""
                {"email":" User@QQ.COM ","code":"012345"}
                """, LoginByCodeRequest.class);
        assertThat(codeLogin.getEmail()).isEqualTo("user@qq.com");
        assertThat(codeLogin.getCode()).isEqualTo("012345");
    }

    @Test
    void preservesDefaultAndExplicitEmailCodePurposes() throws Exception {
        for (String json : new String[]{
                "{\"email\":\" User@QQ.COM \"}",
                "{\"email\":\" User@QQ.COM \",\"purpose\":null}"}) {
            SendEmailCodeRequest request = mapper.readValue(json, SendEmailCodeRequest.class);
            assertThat(request.getEmail()).isEqualTo("user@qq.com");
            assertThat(request.getPurpose()).isEqualTo(EmailCodePurpose.REGISTER);
        }
        SendEmailCodeRequest login = mapper.readValue("""
                {"email":"user@qq.com","purpose":"LOGIN"}
                """, SendEmailCodeRequest.class);
        assertThat(login.getPurpose()).isEqualTo(EmailCodePurpose.LOGIN);
    }
}
