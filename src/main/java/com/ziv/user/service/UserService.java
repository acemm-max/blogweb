package com.ziv.user.service;

import com.ziv.user.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

public interface UserService {
    UserResponse register(RegisterRequest request);
    UserResponse login(LoginRequest request);
    UserResponse loginByPassword(LoginRequest request);
    UserResponse loginByCode(LoginByCodeRequest request);
    UserResponse currentUser(String principal);
    void sendEmailCode(String email);
    void sendEmailCode(String email, EmailCodePurpose purpose);
    UserResponse createSession(LoginRequest body, HttpServletRequest request, HttpServletResponse response);
    UserResponse createCodeSession(LoginByCodeRequest body, HttpServletRequest request, HttpServletResponse response);
    void deleteSession(HttpServletRequest request, HttpServletResponse response, Authentication authentication);
}
