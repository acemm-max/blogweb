package com.blogweb.user.dto;

import com.blogweb.user.entity.User;

public record UserResponse(Long id, String email, String nickname, String role, Integer status) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole(), user.getStatus());
    }
}
