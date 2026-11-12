package com.ziv.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    StoredFile upload(long userId, MultipartFile file);
    String downloadUrl(long userId, String objectKey);
}
