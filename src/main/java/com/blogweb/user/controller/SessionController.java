package com.blogweb.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import com.blogweb.user.dto.LoginRequest;
import com.blogweb.user.dto.LoginByCodeRequest;
import com.blogweb.user.dto.UserResponse;
import com.blogweb.user.service.UserService;
import com.blogweb.common.api.ApiResponse;
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


    @PostMapping({"", "/loginbypassword"})
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> loginByPassword(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(service.createSession(body, request, response));
    }

    @PostMapping("/loginbycode")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> loginByCode(@Valid @RequestBody LoginByCodeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return ApiResponse.success(service.createCodeSession(body, request, response));
    }

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        service.deleteSession(request, response, authentication);
    }
}
