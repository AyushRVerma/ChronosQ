package com.chronosq.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.time.Instant;

import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobScheduleCalculatorTest {

    private JobScheduleCalculator calculator;

    private Instant currentTime;

    @BeforeEach
    void setUp() {

        calculator =
                new JobScheduleCalculator();

        currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );
    }

    @Test
    void shouldMakeImmediateJobReadyNow() {

        var decision =
                calculator.calculateInitialSchedule(
                        ScheduleType.IMMEDIATE,
                        Instant.parse(
                                "2099-01-01T10:00:00Z"
                        ),
                        currentTime
                );

        assertThat(decision.availableAt())
                .isEqualTo(currentTime);

        assertThat(decision.initialStatus())
                .isEqualTo(JobStatus.READY);
    }

    @Test
    void shouldScheduleFutureOneTimeJob() {

        Instant futureTime =
                Instant.parse(
                        "2026-01-01T11:00:00Z"
                );

        var decision =
                calculator.calculateInitialSchedule(
                        ScheduleType.ONE_TIME,
                        futureTime,
                        currentTime
                );

        assertThat(decision.availableAt())
                .isEqualTo(futureTime);

        assertThat(decision.initialStatus())
                .isEqualTo(
                        JobStatus.SCHEDULED
                );
    }

    @Test
    void shouldMakePastOneTimeJobReady() {

        Instant pastTime =
                Instant.parse(
                        "2026-01-01T09:00:00Z"
                );

        var decision =
                calculator.calculateInitialSchedule(
                        ScheduleType.ONE_TIME,
                        pastTime,
                        currentTime
                );

        assertThat(decision.availableAt())
                .isEqualTo(pastTime);

        assertThat(decision.initialStatus())
                .isEqualTo(JobStatus.READY);
    }

    @Test
    void shouldRejectOneTimeJobWithoutAvailableAt() {

        assertThatThrownBy(
                () -> calculator
                        .calculateInitialSchedule(
                                ScheduleType.ONE_TIME,
                                null,
                                currentTime
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "ONE_TIME"
                );
    }

    @Test
    void shouldMakeFixedIntervalJobReadyWhenStartIsMissing() {

        var decision =
                calculator.calculateInitialSchedule(
                        ScheduleType.FIXED_INTERVAL,
                        null,
                        currentTime
                );

        assertThat(decision.availableAt())
                .isEqualTo(currentTime);

        assertThat(decision.initialStatus())
                .isEqualTo(JobStatus.READY);
    }

    @Test
    void shouldScheduleFutureFixedIntervalJob() {

        Instant futureTime =
                Instant.parse(
                        "2026-01-01T10:30:00Z"
                );

        var decision =
                calculator.calculateInitialSchedule(
                        ScheduleType.FIXED_INTERVAL,
                        futureTime,
                        currentTime
                );

        assertThat(decision.availableAt())
                .isEqualTo(futureTime);

        assertThat(decision.initialStatus())
                .isEqualTo(
                        JobStatus.SCHEDULED
                );
    }

    @Test
    void shouldCalculateNextFixedInterval() {

        Instant previousTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Instant now =
                Instant.parse(
                        "2026-01-01T10:01:00Z"
                );

        Instant nextTime =
                calculator.calculateNextFixedInterval(
                        previousTime,
                        300L,
                        now
                );

        assertThat(nextTime)
                .isEqualTo(
                        Instant.parse(
                                "2026-01-01T10:05:00Z"
                        )
                );
    }

    @Test
    void shouldSkipMissedFixedIntervals() {

        Instant previousTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Instant now =
                Instant.parse(
                        "2026-01-01T10:16:00Z"
                );

        Instant nextTime =
                calculator.calculateNextFixedInterval(
                        previousTime,
                        300L,
                        now
                );

        assertThat(nextTime)
                .isEqualTo(
                        Instant.parse(
                                "2026-01-01T10:20:00Z"
                        )
                );
    }

    @Test
    void shouldScheduleAfterExactIntervalBoundary() {

        Instant previousTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Instant now =
                Instant.parse(
                        "2026-01-01T10:15:00Z"
                );

        Instant nextTime =
                calculator.calculateNextFixedInterval(
                        previousTime,
                        300L,
                        now
                );

        assertThat(nextTime)
                .isEqualTo(
                        Instant.parse(
                                "2026-01-01T10:20:00Z"
                        )
                );
    }

    @Test
    void shouldRejectInvalidInterval() {

        assertThatThrownBy(
                () -> calculator
                        .calculateNextFixedInterval(
                                currentTime,
                                0L,
                                currentTime
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "greater than zero"
                );
    }

    @Test
    void shouldRejectNullScheduleType() {

        assertThatThrownBy(
                () -> calculator
                        .calculateInitialSchedule(
                                null,
                                null,
                                currentTime
                        )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "scheduleType must not be null"
                );
    }
}