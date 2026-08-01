package com.chronosq.scheduler;

import static org.assertj.core.api.Assertions
        .assertThatCode;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

    @Mock
    private ScheduledJobService
            scheduledJobService;

    private Instant currentTime;

    private JobScheduler jobScheduler;

    @BeforeEach
    void setUp() {

        currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Clock fixedClock =
                Clock.fixed(
                        currentTime,
                        ZoneOffset.UTC
                );

        jobScheduler =
                new JobScheduler(
                        scheduledJobService,
                        fixedClock
                );
    }

    @Test
    void shouldRunSchedulingCycle() {

        when(
                scheduledJobService
                        .promoteDueJobs(
                                currentTime
                        )
        ).thenReturn(5);

        jobScheduler.runSchedulingCycle();

        verify(scheduledJobService)
                .promoteDueJobs(
                        currentTime
                );
    }

    @Test
    void shouldHandleEmptySchedulingCycle() {

        when(
                scheduledJobService
                        .promoteDueJobs(
                                currentTime
                        )
        ).thenReturn(0);

        assertThatCode(
                () -> jobScheduler
                        .runSchedulingCycle()
        ).doesNotThrowAnyException();

        verify(scheduledJobService)
                .promoteDueJobs(
                        currentTime
                );
    }

    @Test
    void shouldCatchSchedulingFailure() {

        doThrow(
                new IllegalStateException(
                        "Database unavailable"
                )
        )
                .when(scheduledJobService)
                .promoteDueJobs(currentTime);

        assertThatCode(
                () -> jobScheduler
                        .runSchedulingCycle()
        ).doesNotThrowAnyException();

        verify(scheduledJobService)
                .promoteDueJobs(
                        currentTime
                );
    }
}