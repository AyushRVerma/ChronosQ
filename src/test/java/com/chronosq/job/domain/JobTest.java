package com.chronosq.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JobTest {

    private static final UUID JOB_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final Instant NOW =
            Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void shouldCreateValidImmediateJob() {
        Job job = createJob(
                "demo",
                JobStatus.READY,
                ScheduleType.IMMEDIATE,
                null,
                0,
                3,
                null,
                null
        );

        assertThat(job.id()).isEqualTo(JOB_ID);
        assertThat(job.queueName()).isEqualTo("demo");
        assertThat(job.jobType()).isEqualTo("PRINT_MESSAGE");
        assertThat(job.status()).isEqualTo(JobStatus.READY);
        assertThat(job.attemptCount()).isZero();
        assertThat(job.maxAttempts()).isEqualTo(3);
        assertThat(job.isTerminal()).isFalse();
        assertThat(job.hasAttemptsRemaining()).isTrue();
    }

    @Test
    void shouldCreateValidFixedIntervalJob() {
        Job job = createJob(
                "reports",
                JobStatus.SCHEDULED,
                ScheduleType.FIXED_INTERVAL,
                3600L,
                0,
                3,
                null,
                null
        );

        assertThat(job.scheduleType())
                .isEqualTo(ScheduleType.FIXED_INTERVAL);

        assertThat(job.intervalSeconds()).isEqualTo(3600L);
    }

    @Test
    void shouldRecognizeTerminalStatuses() {
        Job succeeded = createJob(
                "demo",
                JobStatus.SUCCEEDED,
                ScheduleType.IMMEDIATE,
                null,
                1,
                3,
                null,
                null
        );

        Job deadLettered = createJob(
                "demo",
                JobStatus.DEAD_LETTERED,
                ScheduleType.IMMEDIATE,
                null,
                3,
                3,
                null,
                null
        );

        Job cancelled = createJob(
                "demo",
                JobStatus.CANCELLED,
                ScheduleType.IMMEDIATE,
                null,
                0,
                3,
                null,
                null
        );

        assertThat(succeeded.isTerminal()).isTrue();
        assertThat(deadLettered.isTerminal()).isTrue();
        assertThat(cancelled.isTerminal()).isTrue();
    }

    @Test
    void shouldCheckWhetherAttemptsRemain() {
        Job retryableJob = createJob(
                "demo",
                JobStatus.RETRY_WAIT,
                ScheduleType.IMMEDIATE,
                null,
                2,
                3,
                null,
                null
        );

        Job exhaustedJob = createJob(
                "demo",
                JobStatus.DEAD_LETTERED,
                ScheduleType.IMMEDIATE,
                null,
                3,
                3,
                null,
                null
        );

        assertThat(retryableJob.hasAttemptsRemaining()).isTrue();
        assertThat(exhaustedJob.hasAttemptsRemaining()).isFalse();
    }

    @Test
    void shouldCheckWhetherJobIsAvailable() {
        Job job = createJob(
                "demo",
                JobStatus.READY,
                ScheduleType.IMMEDIATE,
                null,
                0,
                3,
                null,
                null
        );

        assertThat(
                job.isAvailableAt(NOW.minusSeconds(1))
        ).isFalse();

        assertThat(
                job.isAvailableAt(NOW)
        ).isTrue();

        assertThat(
                job.isAvailableAt(NOW.plusSeconds(1))
        ).isTrue();
    }

    @Test
    void shouldRejectBlankQueueName() {
        assertThatThrownBy(
                () -> createJob(
                        "   ",
                        JobStatus.READY,
                        ScheduleType.IMMEDIATE,
                        null,
                        0,
                        3,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Queue name must not be blank");
    }

    @Test
    void shouldRejectAttemptCountGreaterThanMaximum() {
        assertThatThrownBy(
                () -> createJob(
                        "demo",
                        JobStatus.READY,
                        ScheduleType.IMMEDIATE,
                        null,
                        4,
                        3,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Attempt count must not exceed maximum attempts"
                );
    }

    @Test
    void shouldRejectFixedIntervalWithoutInterval() {
        assertThatThrownBy(
                () -> createJob(
                        "reports",
                        JobStatus.SCHEDULED,
                        ScheduleType.FIXED_INTERVAL,
                        null,
                        0,
                        3,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Fixed-interval jobs require a positive interval"
                );
    }

    @Test
    void shouldRejectIntervalForImmediateJob() {
        assertThatThrownBy(
                () -> createJob(
                        "demo",
                        JobStatus.READY,
                        ScheduleType.IMMEDIATE,
                        60L,
                        0,
                        3,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Only fixed-interval jobs may have an interval"
                );
    }

    @Test
    void shouldRejectOwnerWithoutLease() {
        assertThatThrownBy(
                () -> createJob(
                        "demo",
                        JobStatus.RUNNING,
                        ScheduleType.IMMEDIATE,
                        null,
                        1,
                        3,
                        "worker-a",
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Job owner and lease expiry must both be set or both be null"
                );
    }

    @Test
    void shouldAcceptOwnerWithLease() {
        Job job = createJob(
                "demo",
                JobStatus.RUNNING,
                ScheduleType.IMMEDIATE,
                null,
                1,
                3,
                "worker-a",
                NOW.plusSeconds(30)
        );

        assertThat(job.lockedBy()).isEqualTo("worker-a");

        assertThat(job.leaseExpiresAt())
                .isEqualTo(NOW.plusSeconds(30));
    }

    private static Job createJob(
            String queueName,
            JobStatus status,
            ScheduleType scheduleType,
            Long intervalSeconds,
            int attemptCount,
            int maxAttempts,
            String lockedBy,
            Instant leaseExpiresAt
    ) {
        return new Job(
                JOB_ID,
                queueName,
                "PRINT_MESSAGE",
                """
                {
                  "message": "Hello from ChronosQ"
                }
                """,
                status,
                5,
                NOW,
                scheduleType,
                intervalSeconds,
                attemptCount,
                maxAttempts,
                "test-job-001",
                lockedBy,
                leaseExpiresAt,
                60,
                NOW,
                NOW,
                status == JobStatus.SUCCEEDED ? NOW : null,
                0
        );
    }
}