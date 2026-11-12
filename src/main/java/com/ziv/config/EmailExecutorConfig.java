package com.ziv.config;

import com.ziv.constant.EmailConstant;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class EmailExecutorConfig {
    @Bean("emailExecutor")
    public ThreadPoolTaskExecutor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(EmailConstant.MAIL_THREAD_COUNT);
        executor.setMaxPoolSize(EmailConstant.MAIL_THREAD_COUNT);
        executor.setQueueCapacity(EmailConstant.MAIL_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(EmailConstant.MAIL_THREAD_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(EmailConstant.MAIL_SHUTDOWN_WAIT_SECONDS);
        return executor;
    }
}
