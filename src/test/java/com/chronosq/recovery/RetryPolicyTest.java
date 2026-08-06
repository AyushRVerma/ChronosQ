package com.chronosq.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.chronosq.configuration.RetryProperties;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

class RetryPolicyTest {

    private static final Instant FAILED_AT =
            Instant.parse("2026-08-05T10:00:00Z");

    @Test
    void shouldAllowRetryWhenAttemptsRemain() {
        RetryPolicy retryPolicy = new RetryPolicy(
                defaultProperties()
        );

        Job job = createJob(
                1,
                3
        );

        assertThat(retryPolicy.canRetry(job))
                .isTrue();
    }

    @Test
    void shouldNotAllowRetryWhenAttemptsAreExhausted() {
        RetryPolicy retryPolicy = new RetryPolicy(
                defaultProperties()
        );

        Job job = createJob(
                3,
                3
        );

        assertThat(retryPolicy.canRetry(job))
                .isFalse();
    }

    @Test
    void shouldNotAllowRetryWhenRetryFeatureIsDisabled() {
        RetryProperties disabledProperties =
                new RetryProperties(
                        false,
                        1_000,
                        300_000,
                        2.0,
                        0.0
                );

        RetryPolicy retryPolicy = new RetryPolicy(
                disabledProperties
        );

        Job job = createJob(
                1,
                3
        );

        assertThat(retryPolicy.canRetry(job))
                .isFalse();
    }

    @Test
    void shouldCalculateExponentialBackoffDelay() {
        RetryPolicy retryPolicy = new RetryPolicy(
                defaultProperties()
        );

        assertThat(
                retryPolicy.calculateNextRetryAt(
                        createJob(1, 5),
                        FAILED_AT
                )
        )
                .isEqualTo(
                        FAILED_AT.plusSeconds(1)
                );

        assertThat(
                retryPolicy.calculateNextRetryAt(
                        createJob(2, 5),
                        FAILED_AT
                )
        )
                .isEqualTo(
                        FAILED_AT.plusSeconds(2)
                );

        assertThat(
                retryPolicy.calculateNextRetryAt(
                        createJob(3, 5),
                        FAILED_AT
                )
        )
                .isEqualTo(
                        FAILED_AT.plusSeconds(4)
                );
    }

    @Test
    void shouldNotExceedMaximumDelay() {
        RetryProperties properties =
                new RetryProperties(
                        true,
                        1_000,
                        5_000,
                        2.0,
                        0.0
                );

        RetryPolicy retryPolicy = new RetryPolicy(
                properties
        );

        Instant nextRetryAt =
                retryPolicy.calculateNextRetryAt(
                        createJob(10, 20),
                        FAILED_AT
                );

        assertThat(nextRetryAt)
                .isEqualTo(
                        FAILED_AT.plusSeconds(5)
                );
    }

    private RetryProperties defaultProperties() {
        return new RetryProperties(
                true,
                1_000,
                300_000,
                2.0,
                0.0
        );
    }

    private Job createJob(
            int attemptCount,
            int maxAttempts
    ) {
        return new Job(
                UUID.randomUUID(),
                "default",
                "PRINT_MESSAGE",
                "{\"message\":\"Retry test\"}",
                JobStatus.RUNNING,
                0,
                FAILED_AT,
                ScheduleType.IMMEDIATE,
                null,
                attemptCount,
                maxAttempts,
                null,
                "worker-1",
                FAILED_AT.plusSeconds(60),
                30,
                FAILED_AT,
                FAILED_AT,
                null,
                1L
        );
    }
}