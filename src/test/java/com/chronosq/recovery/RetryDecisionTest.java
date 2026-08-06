package com.chronosq.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RetryDecisionTest {

    @Test
    void shouldCreateRetryDecision() {
        Instant retryAt =
                Instant.parse(
                        "2026-08-06T10:00:05Z"
                );

        RetryDecision decision =
                RetryDecision.retryAt(retryAt);

        assertThat(decision.shouldRetry())
                .isTrue();

        assertThat(decision.nextRetryAt())
                .isEqualTo(retryAt);
    }

    @Test
    void shouldCreateDeadLetterDecision() {
        RetryDecision decision =
                RetryDecision.deadLetter();

        assertThat(decision.shouldRetry())
                .isFalse();

        assertThat(decision.nextRetryAt())
                .isNull();
    }

    @Test
    void shouldRejectRetryWithoutRetryTime() {
        assertThatThrownBy(
                () -> new RetryDecision(
                        true,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "nextRetryAt is required"
                );
    }

    @Test
    void shouldRejectDeadLetterWithRetryTime() {
        Instant retryAt =
                Instant.parse(
                        "2026-08-06T10:00:05Z"
                );

        assertThatThrownBy(
                () -> new RetryDecision(
                        false,
                        retryAt
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "must be null when retry is disabled"
                );
    }
}