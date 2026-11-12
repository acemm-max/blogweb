package com.ziv.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.Message;
import com.ziv.util.ErrorLogUtil;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "blogweb.canal", name = "enabled", havingValue = "true")
public class CanalArticleConsumer {
    private static final Logger log = LoggerFactory.getLogger(CanalArticleConsumer.class);
    @Autowired private CanalConnector connector;
    @Autowired private ArticleChangeProcessor processor;
    @Value("${blogweb.canal.schema}")
    private String schema;
    @Value("${blogweb.canal.destination}")
    private String destination;
    @Value("${blogweb.canal.batch-size}")
    private int batchSize;
    private boolean connected;
    private boolean stopped;

    @Scheduled(fixedDelayString = "${blogweb.canal.poll-delay-ms}")
    public synchronized void poll() {
        if (stopped) return;
        try {
            if (!connected) {
                connector.connect();
                connector.subscribe(schema + "\\.articles");
                connector.rollback();
                connected = true;
                log.info("Canal article consumer connected: destination={}", destination);
            }
            Message message = connector.getWithoutAck(batchSize, 1L, TimeUnit.SECONDS);
            if (message.getId() == -1) return;
            processor.process(message);
            connector.ack(message.getId());
        } catch (Exception exception) {
            ErrorLogUtil.log(log, "Canal article synchronization failed; batch remains unacknowledged", exception);
            try {
                connector.rollback();
            } catch (RuntimeException rollbackFailure) {
                ErrorLogUtil.log(log, "Canal batch rollback failed", rollbackFailure);
            }
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        try {
            connector.disconnect();
        } catch (RuntimeException exception) {
            ErrorLogUtil.log(log, "Canal disconnect failed", exception);
        }
    }

    @PreDestroy
    public synchronized void stop() {
        stopped = true;
        if (connected) disconnect();
    }
}
