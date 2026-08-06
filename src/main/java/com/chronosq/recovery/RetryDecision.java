package com.chronosq.recovery;

import java.time.Instant;

public record RetryDecision(

        boolean shouldRetry,

        Instant nextRetryAt

) {

    public RetryDecision {
        if (shouldRetry && nextRetryAt == null) {
            throw new IllegalArgumentException(
                    "nextRetryAt is required when retry is enabled"
            );
        }

        if (!shouldRetry && nextRetryAt != null) {
            throw new IllegalArgumentException(
                    "nextRetryAt must be null when retry is disabled"
            );
        }
    }

    public static RetryDecision retryAt(Instant nextRetryAt) {
        return new RetryDecision(true, nextRetryAt);
    }

    public static RetryDecision deadLetter() {
        return new RetryDecision(
                false,
                null
        );
    }
}

//In message queues and job schedulers, a Dead Letter Queue (DLQ) or "dead-lettering" is a place where jobs are sent when
// they cannot be processed successfully after maximum retries or due to a permanent error (like the IllegalArgumentException we saw in the
// classifier).