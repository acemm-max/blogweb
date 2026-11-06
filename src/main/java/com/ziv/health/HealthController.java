package com.ziv.health;

import com.ziv.common.ApiResponse;
import com.ziv.common.ApiResponseUtil;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponseUtil.success(Map.of("application", "Blogweb", "status", "UP"));
    }
}
