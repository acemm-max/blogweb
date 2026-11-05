package com.blogweb.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import com.blogweb.user.dto.RegisterRequest;
import com.blogweb.user.dto.EmailCodePurpose;
import com.blogweb.user.dto.SendEmailCodeRequest;
import com.blogweb.user.dto.UserResponse;
import com.blogweb.user.service.UserService;
import com.blogweb.common.api.ApiResponse;
import com.blogweb.common.constant.EmailConstant;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {
    @Autowired
    private UserService service;


    @PostMapping({"/users/register"})
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(service.register(request));
    }

    @GetMapping("/users/me")
    public ApiResponse<UserResponse> currentUser(Authentication authentication) {
        return ApiResponse.success(service.currentUser(authentication.getName()));
    }

    @PostMapping("/email-verification-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SendResult> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        if (request.purpose() == EmailCodePurpose.REGISTER) service.sendEmailCode(request.email());
        else service.sendEmailCode(request.email(), request.purpose());
        return ApiResponse.success(new SendResult(EmailConstant.CODE_TTL_SECONDS, EmailConstant.RESEND_INTERVAL_SECONDS));
    }

    public record SendResult(int expiresInSeconds, int resendAfterSeconds) {
    }
}
