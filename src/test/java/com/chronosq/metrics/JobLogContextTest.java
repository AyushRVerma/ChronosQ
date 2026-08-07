package com.chronosq.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.chronosq.execution.ExecutionStatus;
import com.chronosq.execution.JobExecution;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.worker.ClaimedJob;

class JobLogContextTest {

    private static final UUID JOB_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID EXECUTION_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final Instant STARTED_AT =
            Instant.parse(
                    "2026-08-07T10:00:00Z"
            );

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldAddJobInformationToLoggingContext() {
        ClaimedJob claimedJob =
                createClaimedJob();

        try (JobLogContext ignored =
                     JobLogContext.open(claimedJob)) {

            assertThat(MDC.get("jobId"))
                    .isEqualTo(JOB_ID.toString());

            assertThat(MDC.get("executionId"))
                    .isEqualTo(
                            EXECUTION_ID.toString()
                    );

            assertThat(MDC.get("workerId"))
                    .isEqualTo("worker-1");

            assertThat(MDC.get("jobType"))
                    .isEqualTo("PRINT_MESSAGE");

            assertThat(MDC.get("queueName"))
                    .isEqualTo("default");

            assertThat(MDC.get("attemptNumber"))
                    .isEqualTo("1");
        }
    }

    @Test
    void shouldRemoveJobContextAfterClosing() {
        ClaimedJob claimedJob =
                createClaimedJob();

        try (JobLogContext ignored =
                     JobLogContext.open(claimedJob)) {

            assertThat(MDC.get("jobId"))
                    .isNotNull();
        }

        assertThat(MDC.get("jobId"))
                .isNull();

        assertThat(MDC.get("executionId"))
                .isNull();
    }

    @Test
    void shouldRestorePreviousLoggingContext() {
        MDC.put(
                "traceId",
                "trace-123"
        );

        ClaimedJob claimedJob =
                createClaimedJob();

        try (JobLogContext ignored =
                     JobLogContext.open(claimedJob)) {

            assertThat(MDC.get("jobId"))
                    .isEqualTo(JOB_ID.toString());

            assertThat(MDC.get("traceId"))
                    .isEqualTo("trace-123");
        }

        assertThat(MDC.get("traceId"))
                .isEqualTo("trace-123");

        assertThat(MDC.get("jobId"))
                .isNull();
    }

    private ClaimedJob createClaimedJob() {
        Job job = new Job(
                JOB_ID,
                "default",
                "PRINT_MESSAGE",
                "{\"message\":\"Logging test\"}",
                JobStatus.RUNNING,
                0,
                STARTED_AT,
                ScheduleType.IMMEDIATE,
                null,
                1,
                3,
                null,
                "worker-1",
                STARTED_AT.plusSeconds(60),
                30,
                STARTED_AT,
                STARTED_AT,
                null,
                1L
        );

        JobExecution execution =
                new JobExecution(
                        EXECUTION_ID,
                        JOB_ID,
                        "worker-1",
                        1,
                        ExecutionStatus.RUNNING,
                        STARTED_AT,
                        null,
                        null,
                        null,
                        null
                );

        return new ClaimedJob(
                job,
                execution
        );
    }
}