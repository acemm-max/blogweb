package com.ziv.search;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "blogweb.search")
public class SearchProperties {
    private boolean enabled;
    private String endpoint = "http://localhost:9200";
    private String index = "blogweb-articles";
    private String username = "";
    @ToString.Exclude private String password = "";
}
