package com.chronosq.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.chronosq.execution.ExecutionStatus;
import com.chronosq.execution.JobExecution;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import org.junit.jupiter.api.Test;

class ClaimedJobTest {

    @Test
    void shouldCreateValidClaimedJob() {

        UUID jobId = UUID.randomUUID();

        Job job = createRunningJob(
                jobId,
                "worker-1",
                1
        );

        JobExecution execution =
                createRunningExecution(
                        jobId,
                        "worker-1",
                        1
                );

        ClaimedJob claimedJob =
                new ClaimedJob(
                        job,
                        execution
                );

        assertThat(claimedJob.job())
                .isSameAs(job);

        assertThat(claimedJob.execution())
                .isSameAs(execution);
    }

    @Test
    void shouldRejectNonRunningJob() {

        UUID jobId = UUID.randomUUID();

        Job readyJob = createReadyJob(jobId);

        JobExecution execution =
                createRunningExecution(
                        jobId,
                        "worker-1",
                        1
                );

        assertThatThrownBy(
                () -> new ClaimedJob(
                        readyJob,
                        execution
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "job status"
                );
    }

    @Test
    void shouldRejectExecutionForDifferentJob() {

        Job job = createRunningJob(
                UUID.randomUUID(),
                "worker-1",
                1
        );

        JobExecution execution =
                createRunningExecution(
                        UUID.randomUUID(),
                        "worker-1",
                        1
                );

        assertThatThrownBy(
                () -> new ClaimedJob(
                        job,
                        execution
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "belong"
                );
    }

    @Test
    void shouldRejectDifferentWorker() {

        UUID jobId = UUID.randomUUID();

        Job job = createRunningJob(
                jobId,
                "worker-1",
                1
        );

        JobExecution execution =
                createRunningExecution(
                        jobId,
                        "worker-2",
                        1
                );

        assertThatThrownBy(
                () -> new ClaimedJob(
                        job,
                        execution
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "worker"
                );
    }

    @Test
    void shouldRejectDifferentAttemptNumber() {

        UUID jobId = UUID.randomUUID();

        Job job = createRunningJob(
                jobId,
                "worker-1",
                2
        );

        JobExecution execution =
                createRunningExecution(
                        jobId,
                        "worker-1",
                        1
                );

        assertThatThrownBy(
                () -> new ClaimedJob(
                        job,
                        execution
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "attempt"
                );
    }

    @Test
    void shouldRejectNullJob() {

        JobExecution execution =
                createRunningExecution(
                        UUID.randomUUID(),
                        "worker-1",
                        1
                );

        assertThatThrownBy(
                () -> new ClaimedJob(
                        null,
                        execution
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "job must not be null"
                );
    }

    private Job createRunningJob(
            UUID jobId,
            String workerId,
            int attemptCount
    ) {

        Instant now =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        return new Job(
                jobId,
                "default",
                "PRINT_MESSAGE",
                """
                {"message":"Hello"}
                """,
                JobStatus.RUNNING,
                10,
                now,
                ScheduleType.IMMEDIATE,
                null,
                attemptCount,
                3,
                null,
                workerId,
                now.plusSeconds(60),
                30,
                now.minusSeconds(60),
                now,
                null,
                1L
        );
    }

    private Job createReadyJob(UUID jobId) {

        Instant now =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        return new Job(
                jobId,
                "default",
                "PRINT_MESSAGE",
                """
                {"message":"Hello"}
                """,
                JobStatus.READY,
                10,
                now,
                ScheduleType.IMMEDIATE,
                null,
                0,
                3,
                null,
                null,
                null,
                30,
                now,
                now,
                null,
                0L
        );
    }

    private JobExecution createRunningExecution(
            UUID jobId,
            String workerId,
            int attemptNumber
    ) {

        return new JobExecution(
                UUID.randomUUID(),
                jobId,
                workerId,
                attemptNumber,
                ExecutionStatus.RUNNING,
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                ),
                null,
                null,
                null,
                null
        );
    }
}