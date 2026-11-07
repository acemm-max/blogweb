package com.ziv.user.controller;

import com.ziv.user.infrastructure.EmailCodeStore;
import com.ziv.user.infrastructure.LoginRateLimiter;
import com.ziv.user.entity.User;
import com.ziv.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("standalone")
class UserControllerTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean UserMapper users;
    @MockitoBean EmailCodeStore codes;
    @MockitoBean LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        user.setEmail("123456@qq.com");
        user.setPassword("TestPassword123!");
        user.setNickname("Tester");
        user.setRole("USER");
        user.setStatus(1);
        when(users.selectOne(any(Wrapper.class))).thenReturn(user);
        when(users.selectById(1L)).thenReturn(user);
    }

    @Test
    void sessionLoginMeCsrfRotationAndLogout() throws Exception {
        var csrfResponse = mvc.perform(get("/api/csrf-token")).andReturn();
        var session = (MockHttpSession) csrfResponse.getRequest().getSession(false);
        String oldSessionId = session.getId();
        String oldToken = mapper.readTree(csrfResponse.getResponse().getContentAsString()).at("/data/token").asText();
        mvc.perform(post("/api/sessions").session(session).header("X-CSRF-TOKEN", oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"123456@QQ.COM\",\"password\":\"TestPassword123!\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.password").doesNotExist());
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        mvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(get("/api/admin/example").session(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/sessions/current").session(session).header("X-CSRF-TOKEN", oldToken))
                .andExpect(status().isForbidden());
        var fresh = mvc.perform(get("/api/csrf-token").session(session)).andReturn();
        String newToken = mapper.readTree(fresh.getResponse().getContentAsString()).at("/data/token").asText();
        mvc.perform(delete("/api/sessions/current").session(session).header("X-CSRF-TOKEN", newToken))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationIgnoresClientPrivilegeFields() throws Exception {
        when(users.selectOne(any(Wrapper.class))).thenReturn(null);
        when(codes.consume("654321@qq.com", "012345")).thenReturn(true);
        when(users.insert(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            assertThat(user.getRole()).isEqualTo("USER");
            assertThat(user.getStatus()).isEqualTo(1);
            user.setId(2L);
            return 1;
        });
        mvc.perform(post("/api/users/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"654321@qq.com\",\"password\":\"TestPassword123!\",\"nickname\":\" Tester \",\"code\":\"012345\",\"role\":\"ADMIN\",\"status\":0}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.nickname").value("Tester"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        mvc.perform(post("/api/users/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"123456@qq.com\",\"password\":\"TestPassword123!\",\"nickname\":\"Tester\",\"code\":\"012345\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void badPasswordReturns401WithoutCreatingAuthenticatedSession() throws Exception {
        mvc.perform(post("/api/sessions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"123456@qq.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mvc.perform(post("/api/users/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"short\",\"nickname\":\"\",\"code\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("password: 密码长度必须为8到255个字符")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nickname: 昵称不能为空")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("code: 验证码必须为6位数字")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("short"))));
        verifyNoInteractions(codes);
    }

    @Test
    void registrationRejectsInvalidOrExpiredCodeWithoutInsertingUser() throws Exception {
        when(users.selectOne(any(Wrapper.class))).thenReturn(null);
        when(codes.consume("654321@qq.com", "012345")).thenReturn(false);
        mvc.perform(post("/api/users/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"654321@qq.com\",\"password\":\"TestPassword123!\",\"nickname\":\"Tester\",\"code\":\"012345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Verification code is invalid or expired"));
        verify(users, never()).insert(any(User.class));
    }
    @Test
    void explicitPasswordEndpointCreatesSession() throws Exception {
        mvc.perform(post("/api/sessions/loginbypassword").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"password\":\"TestPassword123!\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void codeEndpointCreatesAuthenticatedSessionAndRejectsReplay() throws Exception {
        when(codes.consume("123456@qq.com", "012345", com.ziv.user.dto.EmailCodePurpose.LOGIN))
                .thenReturn(true).thenReturn(false);
        var result = mvc.perform(post("/api/sessions/loginbycode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"code\":\"012345\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.password").doesNotExist()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/sessions/loginbycode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"code\":\"012345\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void codeEndpointValidatesSixDigitsAndCsrf() throws Exception {
        mvc.perform(post("/api/sessions/loginbycode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"code\":\"123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/sessions/loginbycode").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"code\":\"012345\"}"))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/users/register", "/api/sessions/loginbypassword", "/api/sessions/loginbycode", "/api/sendcode"
    })
    void allAccountEndpointsRejectInvalidEmail(String path) throws Exception {
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@qq.com.evil\",\"password\":\"TestPassword123!\",\"nickname\":\"Tester\",\"code\":\"012345\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(codes, limiter);
        verify(users, never()).insert(any(User.class));
    }

}
