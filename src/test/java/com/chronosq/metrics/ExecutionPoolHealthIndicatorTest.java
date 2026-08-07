package com.chronosq.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ExecutionPoolHealthIndicatorTest {

    private ThreadPoolTaskExecutor taskExecutor;
    private ExecutionPoolHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        taskExecutor = new ThreadPoolTaskExecutor();

        taskExecutor.setCorePoolSize(2);
        taskExecutor.setMaxPoolSize(4);
        taskExecutor.setQueueCapacity(10);
        taskExecutor.setThreadNamePrefix(
                "chronosq-test-"
        );

        taskExecutor.initialize();

        healthIndicator =
                new ExecutionPoolHealthIndicator(
                        taskExecutor
                );
    }

    @AfterEach
    void tearDown() {
        taskExecutor.shutdown();
    }

    @Test
    void shouldReportHealthyExecutionPool() {
        Health health = healthIndicator.health();

        assertThat(health.getStatus())
                .isEqualTo(Status.UP);

        assertThat(health.getDetails())
                .containsEntry(
                        "activeThreads",
                        0
                )
                .containsEntry(
                        "maximumPoolSize",
                        4
                )
                .containsEntry(
                        "queuedJobs",
                        0
                )
                .containsEntry(
                        "remainingQueueCapacity",
                        10
                );
    }

    @Test
    void shouldReportDownWhenExecutionPoolIsStopped() {
        taskExecutor.shutdown();

        Health health = healthIndicator.health();

        assertThat(health.getStatus())
                .isEqualTo(Status.DOWN);

        assertThat(health.getDetails())
                .containsEntry(
                        "reason",
                        "Execution thread pool is stopped"
                );
    }
}