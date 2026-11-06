package com.blogweb.user.controller;

import com.blogweb.user.service.UserService;
import com.blogweb.common.exception.BusinessException;
import com.blogweb.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class EmailCodeControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;

    @Test
    void sendsToNormalizedEmailWithoutExposingCode() throws Exception {
        mvc.perform(post("/api/email-verification-codes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\" 123456@QQ.COM \"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.code").doesNotExist());
        verify(service).sendEmailCode("123456@qq.com");
    }

    @Test
    void rejectsNonComEmail() throws Exception {
        mvc.perform(post("/api/email-verification-codes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.net\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void requiresEmail() throws Exception {
        mvc.perform(post("/api/email-verification-codes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void requiresCsrf() throws Exception {
        mvc.perform(post("/api/email-verification-codes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rateLimitReturns429() throws Exception {
        doThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_CODE_TOO_FREQUENT", "Wait"))
                .when(service).sendEmailCode("123456@qq.com");
        mvc.perform(post("/api/email-verification-codes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\"}")).andExpect(status().isTooManyRequests());
    }
    @Test
    void loginPurposeIsPassedToUserService() throws Exception {
        mvc.perform(post("/api/email-verification-codes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isAccepted());
        verify(service).sendEmailCode("123456@qq.com", com.blogweb.user.dto.EmailCodePurpose.LOGIN);
    }

    @Test
    void unknownPurposeIsRejected() throws Exception {
        mvc.perform(post("/api/email-verification-codes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"123456@qq.com\",\"purpose\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsOtherComDomains() throws Exception {
        mvc.perform(post("/api/email-verification-codes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(service).sendEmailCode("user@example.com");
    }

}
