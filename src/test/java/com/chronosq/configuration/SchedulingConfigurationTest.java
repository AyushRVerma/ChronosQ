package com.chronosq.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.Scheduled;

class SchedulingConfigurationTest {

    @Test
    void periodicTasksDoNotUseTheJobTimeoutMonitor() throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of(
                            "chronosq.scheduler.poll-interval-ms", "1000",
                            "chronosq.scheduler.batch-size", "100",
                            "chronosq.execution.poll-interval-ms", "1000",
                            "chronosq.execution.thread-count", "4",
                            "chronosq.execution.queue-capacity", "100"
                    ))
            );
            context.register(SchedulingConfiguration.class,
                    ExecutionConfiguration.class, ScheduledProbe.class);
            context.refresh();

            ScheduledProbe probe = context.getBean(ScheduledProbe.class);
            assertThat(probe.called.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.threadName.get())
                    .startsWith("chronosq-scheduled-");
        }
    }

    static class ScheduledProbe {
        private final CountDownLatch called = new CountDownLatch(1);
        private final AtomicReference<String> threadName = new AtomicReference<>();

        @Scheduled(fixedDelay = 1000, initialDelay = 0)
        void run() {
            threadName.set(Thread.currentThread().getName());
            called.countDown();
        }
    }
}
