package com.chronosq.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PrintMessagePayloadTest {

    @Test
    void shouldCreateValidPayload() {
        PrintMessagePayload payload =
                new PrintMessagePayload(
                        "Hello ChronosQ"
                );

        assertThat(payload.message())
                .isEqualTo("Hello ChronosQ");
    }

    @Test
    void shouldRemoveSurroundingSpaces() {
        PrintMessagePayload payload =
                new PrintMessagePayload(
                        "   Hello ChronosQ   "
                );

        assertThat(payload.message())
                .isEqualTo("Hello ChronosQ");
    }

    @Test
    void shouldRejectNullMessage() {
        assertThatThrownBy(
                () -> new PrintMessagePayload(null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must not be blank"
                );
    }

    @Test
    void shouldRejectBlankMessage() {
        assertThatThrownBy(
                () -> new PrintMessagePayload("   ")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must not be blank"
                );
    }

    @Test
    void shouldRejectMessageLargerThanMaximumLength() {
        String oversizedMessage =
                "A".repeat(10_001);

        assertThatThrownBy(
                () -> new PrintMessagePayload(
                        oversizedMessage
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must not exceed 10000 characters"
                );
    }
}