package com.ziv.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import java.net.InetSocketAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "blogweb.canal", name = "enabled", havingValue = "true")
public class CanalConfig {
    @Value("${blogweb.canal.host}")
    private String host;
    @Value("${blogweb.canal.port}")
    private int port;
    @Value("${blogweb.canal.destination}")
    private String destination;
    @Value("${blogweb.canal.username}")
    private String username;
    @Value("${blogweb.canal.password}")
    private String password;
    @Value("${blogweb.canal.schema}")
    private String schema;
    @Value("${blogweb.canal.batch-size}")
    private int batchSize;
    @Value("${blogweb.search.enabled}")
    private boolean searchEnabled;

    @Bean
    CanalConnector canalConnector() {
        if (!searchEnabled) throw new IllegalStateException("Canal synchronization requires Elasticsearch enabled");
        if (!schema.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Canal schema must contain only letters, digits and underscores");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalStateException("Canal batch size must be 1-1000");
        }
        return CanalConnectors.newSingleConnector(new InetSocketAddress(host, port), destination, username, password);
    }
}
