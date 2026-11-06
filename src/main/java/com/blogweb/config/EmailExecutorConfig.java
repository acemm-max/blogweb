package com.blogweb.config;

import com.blogweb.common.constant.EmailConstant;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class EmailExecutorConfig {
    @Value("${blogweb.email-executor.queue-capacity:100}")
    private int queueCapacity;

    @Bean("emailExecutor")
    public ThreadPoolTaskExecutor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(EmailConstant.MAIL_THREAD_COUNT);
        executor.setMaxPoolSize(EmailConstant.MAIL_THREAD_COUNT);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("blogweb-email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
