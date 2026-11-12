package com.ziv.storage;

import com.ziv.common.ApiResponse;
import com.ziv.common.ApiResponseUtil;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class StorageController {
    @Autowired private StorageService storage;

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoredFile> upload(@RequestParam MultipartFile file, Authentication authentication) {
        return ApiResponseUtil.success(storage.upload(Long.parseLong(authentication.getName()), file));
    }

    @GetMapping("/download-url")
    public ApiResponse<Map<String, String>> download(@RequestParam String objectKey, Authentication authentication) {
        return ApiResponseUtil.success(Map.of("url", storage.downloadUrl(Long.parseLong(authentication.getName()), objectKey)));
    }
}
