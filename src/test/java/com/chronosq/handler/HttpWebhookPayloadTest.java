package com.chronosq.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class HttpWebhookPayloadTest {

    @Test
    void shouldCreateValidWebhookPayload() {
        HttpWebhookPayload payload =
                new HttpWebhookPayload(
                        "https://example.com/webhook",
                        HttpMethod.POST,
                        Map.of(
                                "X-Source",
                                "ChronosQ"
                        ),
                        null
                );

        assertThat(payload.url())
                .isEqualTo(
                        "https://example.com/webhook"
                );

        assertThat(payload.method())
                .isEqualTo(HttpMethod.POST);

        assertThat(payload.headers())
                .containsEntry(
                        "X-Source",
                        "ChronosQ"
                );
    }

    @Test
    void shouldUsePostAsDefaultMethod() {
        HttpWebhookPayload payload =
                new HttpWebhookPayload(
                        "https://example.com/webhook",
                        null,
                        null,
                        null
                );

        assertThat(payload.method())
                .isEqualTo(HttpMethod.POST);

        assertThat(payload.headers())
                .isEmpty();
    }

    @Test
    void shouldRemoveSpacesAroundUrl() {
        HttpWebhookPayload payload =
                new HttpWebhookPayload(
                        "  https://example.com/webhook  ",
                        HttpMethod.POST,
                        null,
                        null
                );

        assertThat(payload.url())
                .isEqualTo(
                        "https://example.com/webhook"
                );
    }

    @Test
    void shouldRejectBlankUrl() {
        assertThatThrownBy(
                () -> new HttpWebhookPayload(
                        "   ",
                        HttpMethod.POST,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "must not be blank"
                );
    }

    @Test
    void shouldRejectUnsupportedUrlScheme() {
        assertThatThrownBy(
                () -> new HttpWebhookPayload(
                        "ftp://example.com/file",
                        HttpMethod.POST,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "HTTP or HTTPS"
                );
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        assertThatThrownBy(
                () -> new HttpWebhookPayload(
                        "https:///webhook",
                        HttpMethod.POST,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void shouldRejectUnsupportedHttpMethod() {
        assertThatThrownBy(
                () -> new HttpWebhookPayload(
                        "https://example.com/webhook",
                        HttpMethod.GET,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "POST, PUT or PATCH"
                );
    }

    @Test
    void shouldRejectBlankHeaderName() {
        assertThatThrownBy(
                () -> new HttpWebhookPayload(
                        "https://example.com/webhook",
                        HttpMethod.POST,
                        Map.of(
                                "   ",
                                "value"
                        ),
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "header name must not be blank"
                );
    }

    @Test
    void shouldRejectNullHeaderValue() {
        Map<String, String> headers =
                new HashMap<>();

        headers.put(
                "X-Source",
                null
        );

        assertThatThrownBy(
                () -> new HttpWebhookPayload(
                        "https://example.com/webhook",
                        HttpMethod.POST,
                        headers,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void shouldCreateImmutableHeaderMap() {
        Map<String, String> headers =
                new HashMap<>();

        headers.put(
                "X-Source",
                "ChronosQ"
        );

        HttpWebhookPayload payload =
                new HttpWebhookPayload(
                        "https://example.com/webhook",
                        HttpMethod.POST,
                        headers,
                        null
                );

        assertThatThrownBy(
                () -> payload.headers().put(
                        "X-New-Header",
                        "value"
                )
        )
                .isInstanceOf(
                        UnsupportedOperationException.class
                );
    }
}