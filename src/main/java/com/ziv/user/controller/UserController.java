package com.ziv.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import com.ziv.user.dto.RegisterRequest;
import com.ziv.user.dto.EmailCodePurpose;
import com.ziv.user.dto.SendEmailCodeRequest;
import com.ziv.user.dto.UserResponse;
import com.ziv.user.service.UserService;
import com.ziv.common.ApiResponse;
import com.ziv.common.ApiResponseUtil;
import com.ziv.constant.EmailConstant;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
        return ApiResponseUtil.success(service.register(request));
    }

    @GetMapping("/users/me")
    public ApiResponse<UserResponse> currentUser(Authentication authentication) {
        return ApiResponseUtil.success(service.currentUser(authentication.getName()));
    }

    @PostMapping("/sendcode")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<SendResult> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        if (request.getPurpose() == EmailCodePurpose.REGISTER) service.sendEmailCode(request.getEmail());
        else service.sendEmailCode(request.getEmail(), request.getPurpose());
        return ApiResponseUtil.success(new SendResult("QUEUED", EmailConstant.CODE_TTL_SECONDS, EmailConstant.RESEND_INTERVAL_SECONDS));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendResult {
        private String status;
        private int expiresInSeconds;
        private int resendAfterSeconds;
    }
}
