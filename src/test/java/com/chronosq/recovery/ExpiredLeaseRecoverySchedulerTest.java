package com.chronosq.recovery;

import static org.assertj.core.api.Assertions.assertThatCode;
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
class ExpiredLeaseRecoverySchedulerTest {

    private static final Instant CURRENT_TIME =
            Instant.parse("2026-08-06T10:00:00Z");

    @Mock
    private ExpiredLeaseRecoveryService recoveryService;

    private ExpiredLeaseRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                CURRENT_TIME,
                ZoneOffset.UTC
        );

        scheduler = new ExpiredLeaseRecoveryScheduler(
                recoveryService,
                clock
        );
    }

    @Test
    void shouldRecoverExpiredJobsUsingCurrentTime() {
        when(recoveryService.recoverExpiredJobs(
                CURRENT_TIME
        )).thenReturn(
                RecoveryResult.empty()
        );

        scheduler.recoverExpiredJobs();

        verify(recoveryService)
                .recoverExpiredJobs(CURRENT_TIME);
    }

    @Test
    void shouldNotCrashWhenRecoveryFails() {
        when(recoveryService.recoverExpiredJobs(
                CURRENT_TIME
        )).thenThrow(
                new IllegalStateException(
                        "Database temporarily unavailable"
                )
        );

        assertThatCode(
                scheduler::recoverExpiredJobs
        ).doesNotThrowAnyException();
    }
}