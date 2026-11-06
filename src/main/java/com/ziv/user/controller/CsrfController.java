package com.ziv.user.controller;

import com.ziv.common.ApiResponse;
import com.ziv.common.ApiResponseUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

    @GetMapping("/api/csrf-token")
    public ApiResponse<CsrfData> csrf(CsrfToken token) {
        return ApiResponseUtil.success(new CsrfData(token.getHeaderName(), token.getToken()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsrfData {
        private String headerName;
        private String token;
    }
}
