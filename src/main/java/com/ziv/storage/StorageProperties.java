package com.ziv.storage;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "blogweb.storage")
public class StorageProperties {
    private boolean enabled;
    private String endpoint = "http://localhost:9000";
    private String accessKey = "";
    @ToString.Exclude
    private String secretKey = "";
    private String bucket = "blogweb";
    private int urlExpirySeconds = 600;
}
