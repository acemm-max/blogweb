package com.ziv.user.controller;

import com.ziv.user.service.UserService;
import com.ziv.exception.BusinessException;
import com.ziv.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({UserController.class, CsrfController.class})
@Import(SecurityConfig.class)
class EmailCodeControllerTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean UserService service;

    @Test
    void anonymousClientCanFetchTokenAndSendCodeWithSameSession() throws Exception {
        var result = mvc.perform(get("/api/csrf-token"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        var data = mapper.readTree(result.getResponse().getContentAsString()).get("data");
        mvc.perform(post("/api/sendcode").session(session)
                        .header(data.get("headerName").asText(), data.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"123456@qq.com\"}"))
                .andExpect(status().isAccepted());
        verify(service).sendEmailCode("123456@qq.com");
    }

    @Test
    void rejectsInvalidCsrfToken() throws Exception {
        mvc.perform(post("/api/sendcode").with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"123456@qq.com\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void sendsToNormalizedEmailWithoutExposingCode() throws Exception {
        mvc.perform(post("/api/sendcode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\" 123456@QQ.COM \"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.code").doesNotExist());
        verify(service).sendEmailCode("123456@qq.com");
    }

    @Test
    void requiresEmail() throws Exception {
        mvc.perform(post("/api/sendcode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void requiresCsrf() throws Exception {
        mvc.perform(post("/api/sendcode").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rateLimitReturns429() throws Exception {
        doThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS, 429, "Wait"))
                .when(service).sendEmailCode("123456@qq.com");
        mvc.perform(post("/api/sendcode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\"}")).andExpect(status().isTooManyRequests());
    }
    @Test
    void loginPurposeIsPassedToUserService() throws Exception {
        mvc.perform(post("/api/sendcode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isAccepted());
        verify(service).sendEmailCode("123456@qq.com", com.ziv.user.dto.EmailCodePurpose.LOGIN);
    }

    @Test
    void unknownPurposeIsRejected() throws Exception {
        mvc.perform(post("/api/sendcode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"purpose\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsOtherComDomains() throws Exception {
        mvc.perform(post("/api/sendcode").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(service).sendEmailCode("user@example.com");
    }

}
