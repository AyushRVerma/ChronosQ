package com.chronosq.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronosq.handler.UnknownJobTypeException;
import com.chronosq.handler.WebhookDeliveryException;

class RetryableFailureClassifierTest {

    private RetryableFailureClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RetryableFailureClassifier();
    }

    @Test
    void shouldRetryTemporaryWebhookFailure() {
        boolean retryable = classifier.isRetryable(
                new WebhookDeliveryException(503)
        );

        assertThat(retryable)
                .isTrue();
    }

    @Test
    void shouldNotRetryInvalidWebhookRequest() {
        boolean retryable = classifier.isRetryable(
                new WebhookDeliveryException(400)
        );

        assertThat(retryable)
                .isFalse();
    }

    @Test
    void shouldNotRetryInvalidJobData() {
        boolean retryable = classifier.isRetryable(
                new IllegalArgumentException(
                        "Payload is invalid"
                )
        );

        assertThat(retryable)
                .isFalse();
    }

    @Test
    void shouldNotRetryUnknownJobType() {
        boolean retryable = classifier.isRetryable(
                new UnknownJobTypeException(
                        "UNKNOWN_JOB_TYPE"
                )
        );

        assertThat(retryable)
                .isFalse();
    }

    @Test
    void shouldNotRetryWrappedPermanentFailure() {
        RuntimeException wrappedFailure =
                new RuntimeException(
                        "Could not process job",
                        new IllegalArgumentException(
                                "Payload is invalid"
                        )
                );

        boolean retryable = classifier.isRetryable(
                wrappedFailure
        );

        assertThat(retryable)
                .isFalse();
    }

    @Test
    void shouldRetryUnexpectedFailureByDefault() {
        boolean retryable = classifier.isRetryable(
                new IllegalStateException(
                        "Temporary service problem"
                )
        );

        assertThat(retryable)
                .isTrue();
    }
}