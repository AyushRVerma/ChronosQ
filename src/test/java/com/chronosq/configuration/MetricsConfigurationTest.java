package com.chronosq.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronosq.metrics.MetricNames;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MetricsConfigurationTest {

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        MetricsConfiguration configuration =
                new MetricsConfiguration();

        configuration
                .metricsCommonTags()
                .customize(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void shouldAddApplicationTagToMetrics() {
        Counter counter = Counter.builder(
                        MetricNames.JOBS_SUBMITTED
                )
                .register(meterRegistry);

        counter.increment();

        assertThat(
                counter.getId().getTag("application")
        ).isEqualTo("chronosq");

        assertThat(counter.count())
                .isEqualTo(1.0);
    }
}