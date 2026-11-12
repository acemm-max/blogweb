package com.ziv.search;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SearchConfig {
    @Bean("elasticsearchHttp")
    @ConditionalOnProperty(prefix = "blogweb.search", name = "enabled", havingValue = "true")
    RestClient elasticsearchHttp(SearchProperties properties) {
        if (!properties.getIndex().matches("[a-z][a-z0-9_-]{0,100}")) {
            throw new IllegalStateException("Invalid Elasticsearch index name");
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getEndpoint()).requestFactory(requests);
        if (!properties.getUsername().isBlank()) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(properties.getUsername(), properties.getPassword()));
        }
        return builder.build();
    }
}
