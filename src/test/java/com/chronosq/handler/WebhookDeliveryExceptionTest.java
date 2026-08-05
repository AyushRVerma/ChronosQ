package com.chronosq.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookDeliveryExceptionTest {

    @Test
    void shouldStoreHttpStatusCode() {
        WebhookDeliveryException exception =
                new WebhookDeliveryException(502);

        assertThat(exception.statusCode())
                .isEqualTo(502);

        assertThat(exception.getMessage())
                .isEqualTo(
                        "Webhook delivery failed with HTTP status 502"
                );
    }

    @ParameterizedTest
    @ValueSource(ints = {
            408,
            425,
            429,
            500,
            502,
            503
    })
    void shouldMarkTemporaryFailuresAsRetryable(
            int statusCode
    ) {
        WebhookDeliveryException exception =
                new WebhookDeliveryException(statusCode);

        assertThat(exception.isRetryable())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {
            400,
            401,
            403,
            404,
            422
    })
    void shouldNotRetryInvalidClientRequests(
            int statusCode
    ) {
        WebhookDeliveryException exception =
                new WebhookDeliveryException(statusCode);

        assertThat(exception.isRetryable())
                .isFalse();
    }
}