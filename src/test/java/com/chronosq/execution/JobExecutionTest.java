package com.chronosq.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JobExecutionTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID JOB_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final Instant STARTED_AT =
            Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void shouldCreateRunningExecution() {
        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                JOB_ID,
                "worker-a",
                1,
                ExecutionStatus.RUNNING,
                STARTED_AT,
                null,
                null,
                null,
                null
        );

        assertThat(execution.id()).isEqualTo(EXECUTION_ID);
        assertThat(execution.jobId()).isEqualTo(JOB_ID);
        assertThat(execution.workerId()).isEqualTo("worker-a");
        assertThat(execution.attemptNumber()).isEqualTo(1);
        assertThat(execution.isFinished()).isFalse();
        assertThat(execution.wasSuccessful()).isFalse();
        assertThat(execution.failed()).isFalse();
    }

    @Test
    void shouldCreateSuccessfulExecution() {
        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                JOB_ID,
                "worker-a",
                1,
                ExecutionStatus.SUCCEEDED,
                STARTED_AT,
                STARTED_AT.plusSeconds(2),
                2000L,
                null,
                null
        );

        assertThat(execution.isFinished()).isTrue();
        assertThat(execution.wasSuccessful()).isTrue();
        assertThat(execution.failed()).isFalse();
        assertThat(execution.durationMs()).isEqualTo(2000L);
    }

    @Test
    void shouldCreateFailedExecution() {
        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                JOB_ID,
                "worker-a",
                2,
                ExecutionStatus.FAILED,
                STARTED_AT,
                STARTED_AT.plusMillis(500),
                500L,
                "TemporaryServiceException",
                "Temporary service failure"
        );

        assertThat(execution.isFinished()).isTrue();
        assertThat(execution.wasSuccessful()).isFalse();
        assertThat(execution.failed()).isTrue();

        assertThat(execution.errorType())
                .isEqualTo("TemporaryServiceException");

        assertThat(execution.errorMessage())
                .isEqualTo("Temporary service failure");
    }

    @Test
    void shouldRecognizeTimedOutExecutionAsFailure() {
        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                JOB_ID,
                "worker-a",
                1,
                ExecutionStatus.TIMED_OUT,
                STARTED_AT,
                STARTED_AT.plusSeconds(30),
                30_000L,
                "JobTimeoutException",
                "Job exceeded its execution timeout"
        );

        assertThat(execution.failed()).isTrue();
        assertThat(execution.isFinished()).isTrue();
    }

    @Test
    void shouldRecognizeAbandonedExecutionAsFailure() {
        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                JOB_ID,
                "worker-a",
                1,
                ExecutionStatus.ABANDONED,
                STARTED_AT,
                STARTED_AT.plusSeconds(35),
                35_000L,
                "LeaseExpiredException",
                "Worker lease expired"
        );

        assertThat(execution.failed()).isTrue();
        assertThat(execution.isFinished()).isTrue();
    }

    @Test
    void shouldRejectAttemptNumberBelowOne() {
        assertThatThrownBy(
                () -> new JobExecution(
                        EXECUTION_ID,
                        JOB_ID,
                        "worker-a",
                        0,
                        ExecutionStatus.RUNNING,
                        STARTED_AT,
                        null,
                        null,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Attempt number must be at least 1");
    }

    @Test
    void shouldRejectNegativeDuration() {
        assertThatThrownBy(
                () -> new JobExecution(
                        EXECUTION_ID,
                        JOB_ID,
                        "worker-a",
                        1,
                        ExecutionStatus.FAILED,
                        STARTED_AT,
                        STARTED_AT.plusSeconds(1),
                        -1L,
                        "TestException",
                        "Test failure"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Execution duration must not be negative"
                );
    }

    @Test
    void shouldRejectFinishTimeBeforeStartTime() {
        assertThatThrownBy(
                () -> new JobExecution(
                        EXECUTION_ID,
                        JOB_ID,
                        "worker-a",
                        1,
                        ExecutionStatus.FAILED,
                        STARTED_AT,
                        STARTED_AT.minusSeconds(1),
                        100L,
                        "TestException",
                        "Test failure"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Execution finish time must not be before start time"
                );
    }

    @Test
    void shouldRejectRunningExecutionWithFinishInformation() {
        assertThatThrownBy(
                () -> new JobExecution(
                        EXECUTION_ID,
                        JOB_ID,
                        "worker-a",
                        1,
                        ExecutionStatus.RUNNING,
                        STARTED_AT,
                        STARTED_AT.plusSeconds(1),
                        1000L,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Running execution must not have finish information"
                );
    }

    @Test
    void shouldRejectFinishedExecutionWithoutFinishInformation() {
        assertThatThrownBy(
                () -> new JobExecution(
                        EXECUTION_ID,
                        JOB_ID,
                        "worker-a",
                        1,
                        ExecutionStatus.SUCCEEDED,
                        STARTED_AT,
                        null,
                        null,
                        null,
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Finished execution requires finish time and duration"
                );
    }

    @Test
    void shouldRejectSuccessfulExecutionWithErrorInformation() {
        assertThatThrownBy(
                () -> new JobExecution(
                        EXECUTION_ID,
                        JOB_ID,
                        "worker-a",
                        1,
                        ExecutionStatus.SUCCEEDED,
                        STARTED_AT,
                        STARTED_AT.plusSeconds(1),
                        1000L,
                        "UnexpectedError",
                        "This should not exist"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Successful execution must not have error information"
                );
    }
}