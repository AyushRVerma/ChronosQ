package com.chronosq.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronosq.configuration.RetryProperties;
import com.chronosq.handler.WebhookDeliveryException;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

class RetryDecisionServiceTest {

    private static final Instant FAILED_AT =
            Instant.parse("2026-08-06T10:00:00Z");

    private RetryDecisionService retryDecisionService;

    @BeforeEach
    void setUp() {
        RetryProperties properties =
                new RetryProperties(
                        true,
                        1_000,
                        300_000,
                        2.0,
                        0.0
                );

        RetryPolicy retryPolicy =
                new RetryPolicy(properties);

        RetryableFailureClassifier classifier =
                new RetryableFailureClassifier();

        retryDecisionService =
                new RetryDecisionService(
                        retryPolicy,
                        classifier
                );
    }

    @Test
    void shouldRetryTemporaryFailureWhenAttemptsRemain() {
        RetryDecision decision =
                retryDecisionService.decide(
                        createJob(1, 3),
                        new WebhookDeliveryException(503),
                        FAILED_AT
                );

        assertThat(decision.shouldRetry())
                .isTrue();

        assertThat(decision.nextRetryAt())
                .isEqualTo(
                        FAILED_AT.plusSeconds(1)
                );
    }

    @Test
    void shouldDeadLetterPermanentFailure() {
        RetryDecision decision =
                retryDecisionService.decide(
                        createJob(1, 3),
                        new WebhookDeliveryException(400),
                        FAILED_AT
                );

        assertThat(decision.shouldRetry())
                .isFalse();

        assertThat(decision.nextRetryAt())
                .isNull();
    }

    @Test
    void shouldDeadLetterWhenAttemptsAreExhausted() {
        RetryDecision decision =
                retryDecisionService.decide(
                        createJob(3, 3),
                        new WebhookDeliveryException(503),
                        FAILED_AT
                );

        assertThat(decision.shouldRetry())
                .isFalse();

        assertThat(decision.nextRetryAt())
                .isNull();
    }

    private Job createJob(
            int attemptCount,
            int maxAttempts
    ) {
        return new Job(
                UUID.randomUUID(),
                "default",
                "HTTP_WEBHOOK",
                "{\"url\":\"https://example.com/webhook\"}",
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