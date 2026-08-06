package com.chronosq.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronosq.configuration.RecoveryProperties;
import com.chronosq.configuration.RetryProperties;
import com.chronosq.execution.JobExecutionRepository;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

@ExtendWith(MockitoExtension.class)
class ExpiredLeaseRecoveryServiceTest {

    private static final UUID JOB_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String WORKER_ID = "worker-1";

    private static final Instant RECOVERY_TIME =
            Instant.parse("2026-08-06T10:00:00Z");

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository jobExecutionRepository;

    private ExpiredLeaseRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        RecoveryProperties recoveryProperties =
                new RecoveryProperties(
                        true,
                        10_000,
                        100
                );

        RetryProperties retryProperties =
                new RetryProperties(
                        true,
                        1_000,
                        300_000,
                        2.0,
                        0.0
                );

        recoveryService =
                new ExpiredLeaseRecoveryService(
                        jobRepository,
                        jobExecutionRepository,
                        new RetryPolicy(retryProperties),
                        recoveryProperties
                );
    }

    @Test
    void shouldRecoverExpiredJobForRetry() {
        Job expiredJob = createRunningJob(
                1,
                3
        );

        when(jobRepository.findExpiredRunningJobs(
                RECOVERY_TIME,
                100
        )).thenReturn(
                List.of(expiredJob)
        );

        when(jobExecutionRepository.abandonRunningExecution(
                JOB_ID,
                WORKER_ID,
                RECOVERY_TIME
        )).thenReturn(true);

        when(jobRepository.recoverExpiredRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.RETRY_WAIT,
                RECOVERY_TIME.plusSeconds(1),
                RECOVERY_TIME,
                1L
        )).thenReturn(true);

        RecoveryResult result =
                recoveryService.recoverExpiredJobs(
                        RECOVERY_TIME
                );

        assertThat(result.retriedJobCount())
                .isEqualTo(1);

        assertThat(result.deadLetteredJobCount())
                .isZero();

        verify(jobExecutionRepository)
                .abandonRunningExecution(
                        JOB_ID,
                        WORKER_ID,
                        RECOVERY_TIME
                );

        verify(jobRepository)
                .recoverExpiredRunningJob(
                        JOB_ID,
                        WORKER_ID,
                        JobStatus.RETRY_WAIT,
                        RECOVERY_TIME.plusSeconds(1),
                        RECOVERY_TIME,
                        1L
                );
    }

    @Test
    void shouldDeadLetterExpiredJobWithoutAttemptsRemaining() {
        Job expiredJob = createRunningJob(
                3,
                3
        );

        when(jobRepository.findExpiredRunningJobs(
                RECOVERY_TIME,
                100
        )).thenReturn(
                List.of(expiredJob)
        );

        when(jobExecutionRepository.abandonRunningExecution(
                JOB_ID,
                WORKER_ID,
                RECOVERY_TIME
        )).thenReturn(true);

        when(jobRepository.recoverExpiredRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.DEAD_LETTERED,
                RECOVERY_TIME,
                RECOVERY_TIME,
                1L
        )).thenReturn(true);

        RecoveryResult result =
                recoveryService.recoverExpiredJobs(
                        RECOVERY_TIME
                );

        assertThat(result.retriedJobCount())
                .isZero();

        assertThat(result.deadLetteredJobCount())
                .isEqualTo(1);
    }

    private Job createRunningJob(
            int attemptCount,
            int maxAttempts
    ) {
        return new Job(
                JOB_ID,
                "default",
                "PRINT_MESSAGE",
                "{\"message\":\"Recovery test\"}",
                JobStatus.RUNNING,
                0,
                RECOVERY_TIME.minusSeconds(60),
                ScheduleType.IMMEDIATE,
                null,
                attemptCount,
                maxAttempts,
                null,
                WORKER_ID,
                RECOVERY_TIME.minusSeconds(1),
                30,
                RECOVERY_TIME.minusSeconds(60),
                RECOVERY_TIME.minusSeconds(60),
                null,
                1L
        );
    }
}