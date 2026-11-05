package com.blogweb.user.controller;

import com.blogweb.common.api.ApiResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

    @GetMapping("/api/csrf-token")
    public ApiResponse<CsrfData> csrf(CsrfToken token) {
        return ApiResponse.success(new CsrfData(token.getHeaderName(), token.getToken()));
    }

    public record CsrfData(String headerName, String token) {
    }
}
