package com.chronosq.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronosq.execution.ExecutionStatus;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ChronosQMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private ChronosQMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        metrics = new ChronosQMetrics(
                meterRegistry
        );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void shouldRecordSubmittedJobs() {
        metrics.incrementJobSubmitted();
        metrics.incrementJobSubmitted();

        assertThat(
                meterRegistry
                        .find("chronosq.jobs.submitted")
                        .counter()
                        .count()
        ).isEqualTo(2.0);
    }

    @Test
    void shouldRecordClaimedJobs() {
        metrics.incrementJobsClaimed(5);

        assertThat(
                meterRegistry
                        .find("chronosq.jobs.claimed")
                        .counter()
                        .count()
        ).isEqualTo(5.0);
    }

    @Test
    void shouldRecordSuccessfulExecution() {
        metrics.recordExecution(
                ExecutionStatus.SUCCEEDED,
                250
        );

        assertThat(
                meterRegistry
                        .find("chronosq.executions.completed")
                        .tag(
                                "status",
                                "SUCCEEDED"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1.0);

        Timer timer = meterRegistry
                .find("chronosq.execution.duration")
                .tag(
                        "status",
                        "SUCCEEDED"
                )
                .timer();

        assertThat(timer.count())
                .isEqualTo(1);

        assertThat(timer.totalTime(
                java.util.concurrent.TimeUnit.MILLISECONDS
        )).isEqualTo(250.0);
    }

    @Test
    void shouldRecordRetryDeadLetterAndRecovery() {
        metrics.incrementRetryScheduled();
        metrics.incrementJobDeadLettered();
        metrics.incrementExpiredLeaseRecovered();

        assertThat(
                meterRegistry
                        .find(
                                "chronosq.jobs.retries.scheduled"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1.0);

        assertThat(
                meterRegistry
                        .find(
                                "chronosq.jobs.dead_lettered"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1.0);

        assertThat(
                meterRegistry
                        .find(
                                "chronosq.jobs.leases.recovered"
                        )
                        .counter()
                        .count()
        ).isEqualTo(1.0);
    }
}