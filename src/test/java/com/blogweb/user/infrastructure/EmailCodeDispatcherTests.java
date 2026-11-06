package com.blogweb.user.infrastructure;

import com.blogweb.common.util.EmailUtil;
import com.blogweb.config.EmailExecutorConfig;
import com.blogweb.user.dto.EmailCodePurpose;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.mail2.core.EmailException;
import org.junit.jupiter.api.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailCodeDispatcherTests {
    private final EmailCodeStore codes = mock(EmailCodeStore.class);
    private final EmailUtil mail = mock(EmailUtil.class);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch completed = new CountDownLatch(1);
    private ThreadPoolTaskExecutor executor;
    private EmailCodeDispatcher dispatcher;

    @BeforeEach
    void setup() {
        EmailExecutorConfig config = new EmailExecutorConfig();
        ReflectionTestUtils.setField(config, "queueCapacity", 2);
        executor = config.emailExecutor();
        executor.setTaskDecorator(task -> () -> {
            try { task.run(); } finally { completed.countDown(); }
        });
        executor.initialize();
        dispatcher = new EmailCodeDispatcher();
        ReflectionTestUtils.setField(dispatcher, "executor", executor);
        ReflectionTestUtils.setField(dispatcher, "codes", codes);
        ReflectionTestUtils.setField(dispatcher, "emailUtil", mail);
        when(codes.isCurrent(anyString(), anyString(), any())).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        release.countDown();
        executor.shutdown();
    }

    @Test
    void submitReturnsWhileMailIsStillSendingOnAnotherThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        doAnswer(call -> {
            worker.set(Thread.currentThread());
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(mail).sendCode(anyString(), anyString());
        dispatcher.submit("user@qq.com", "012345", "reservation", EmailCodePurpose.LOGIN);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(completed.getCount()).isEqualTo(1);
        assertThat(worker.get()).isNotSameAs(Thread.currentThread());
        assertThat(worker.get().getName()).startsWith("blogweb-email-");
    }

    @Test
    void exactlyFiveWorkersAndBoundedQueueRejectOverflow() throws Exception {
        CountDownLatch started = new CountDownLatch(5);
        doAnswer(call -> {
            started.countDown();
            release.await(10, TimeUnit.SECONDS);
            return null;
        }).when(mail).sendCode(anyString(), anyString());
        for (int i = 0; i < 7; i++) dispatcher.submit("user@qq.com", "012345", "r" + i, EmailCodePurpose.LOGIN);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.getCorePoolSize()).isEqualTo(5);
        assertThat(executor.getMaxPoolSize()).isEqualTo(5);
        assertThat(executor.getPoolSize()).isEqualTo(5);
        assertThat(executor.getQueueSize()).isEqualTo(2);
        assertThatThrownBy(() -> dispatcher.submit("user@qq.com", "012345", "overflow", EmailCodePurpose.LOGIN))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void backgroundFailureInvalidatesItsCode() throws Exception {
        doThrow(new EmailException("SMTP unavailable")).when(mail).sendCode(anyString(), anyString());
        dispatcher.submit("user@qq.com", "012345", "reservation", EmailCodePurpose.LOGIN);
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        verify(codes).invalidate("user@qq.com", "reservation", EmailCodePurpose.LOGIN);
    }

    @Test
    void staleQueuedTaskNeverSends() throws Exception {
        when(codes.isCurrent(anyString(), anyString(), any())).thenReturn(false);
        dispatcher.submit("user@qq.com", "012345", "old", EmailCodePurpose.LOGIN);
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        verifyNoInteractions(mail);
    }
}
