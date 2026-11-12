package com.ziv.storage;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {
    @Bean
    @ConditionalOnProperty(prefix = "blogweb.storage", name = "enabled", havingValue = "true")
    MinioClient minioClient(StorageProperties properties) {
        if (properties.getAccessKey().isBlank() || properties.getSecretKey().isBlank()) {
            throw new IllegalStateException("MinIO access key and secret key must be configured");
        }
        if (properties.getUrlExpirySeconds() < 1 || properties.getUrlExpirySeconds() > 604800) {
            throw new IllegalStateException("MinIO URL expiry must be between 1 and 604800 seconds");
        }
        return MinioClient.builder().endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey()).build();
    }
}
