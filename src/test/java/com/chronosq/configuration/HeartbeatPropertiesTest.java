package com.chronosq.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeartbeatPropertiesTest {

    @Test
    void shouldCreateValidHeartbeatProperties() {
        HeartbeatProperties properties =
                new HeartbeatProperties(
                        true,
                        5_000
                );

        assertThat(properties.enabled())
                .isTrue();

        assertThat(properties.intervalMs())
                .isEqualTo(5_000);
    }

    @Test
    void shouldAllowHeartbeatToBeDisabled() {
        HeartbeatProperties properties =
                new HeartbeatProperties(
                        false,
                        5_000
                );

        assertThat(properties.enabled())
                .isFalse();
    }
}