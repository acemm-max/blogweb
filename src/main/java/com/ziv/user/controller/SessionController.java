package com.ziv.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import com.ziv.user.dto.LoginByPasswordRequest;
import com.ziv.user.dto.LoginByCodeRequest;
import com.ziv.user.dto.UserResponse;
import com.ziv.user.service.UserService;
import com.ziv.common.ApiResponse;
import com.ziv.common.ApiResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    @Autowired
    private UserService service;


    @PostMapping({"/loginbypassword"})
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> loginByPassword(@Valid @RequestBody LoginByPasswordRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return ApiResponseUtil.success(service.createSession(body, request, response));
    }

    @PostMapping("/loginbycode")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> loginByCode(@Valid @RequestBody LoginByCodeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return ApiResponseUtil.success(service.createCodeSession(body, request, response));
    }

    @DeleteMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        service.deleteSession(request, response, authentication);
    }
}
