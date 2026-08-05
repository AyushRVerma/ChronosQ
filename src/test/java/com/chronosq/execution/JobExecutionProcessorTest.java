package com.chronosq.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronosq.handler.JobHandler;
import com.chronosq.handler.JobHandlerRegistry;
import com.chronosq.handler.UnknownJobTypeException;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.worker.ClaimedJob;

@ExtendWith(MockitoExtension.class)
class JobExecutionProcessorTest {

    private static final UUID JOB_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID EXECUTION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String WORKER_ID = "worker-1";

    private static final Instant CURRENT_TIME =
            Instant.parse("2026-08-05T10:00:00Z");

    @Mock
    private JobHandlerRegistry jobHandlerRegistry;

    @Mock
    private JobExecutionCompletionService completionService;

    @Mock
    private JobHandler jobHandler;

    private JobExecutionProcessor processor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                CURRENT_TIME,
                ZoneOffset.UTC
        );

        processor = new JobExecutionProcessor(
                jobHandlerRegistry,
                completionService,
                clock
        );
    }

    @Test
    void shouldExecuteJobSuccessfully() throws Exception {
        ClaimedJob claimedJob = createClaimedJob();

        when(jobHandlerRegistry.getRequiredHandler("PRINT_MESSAGE"))
                .thenReturn(jobHandler);

        processor.process(claimedJob);

        verify(jobHandler).execute(claimedJob.job());

        ArgumentCaptor<ExecutionResult> resultCaptor =
                ArgumentCaptor.forClass(ExecutionResult.class);

        verify(completionService).complete(
                org.mockito.ArgumentMatchers.eq(claimedJob),
                resultCaptor.capture()
        );

        ExecutionResult result = resultCaptor.getValue();

        assertThat(result.status())
                .isEqualTo(ExecutionStatus.SUCCEEDED);

        assertThat(result.finishedAt())
                .isEqualTo(CURRENT_TIME);

        assertThat(result.durationMs())
                .isZero();

        assertThat(result.errorType())
                .isNull();

        assertThat(result.errorMessage())
                .isNull();
    }

    @Test
    void shouldRecordFailureWhenHandlerThrowsException() throws Exception {
        ClaimedJob claimedJob = createClaimedJob();

        when(jobHandlerRegistry.getRequiredHandler("PRINT_MESSAGE"))
                .thenReturn(jobHandler);

        doThrow(new IllegalStateException("Handler failed"))
                .when(jobHandler)
                .execute(claimedJob.job());

        processor.process(claimedJob);

        ArgumentCaptor<ExecutionResult> resultCaptor =
                ArgumentCaptor.forClass(ExecutionResult.class);

        verify(completionService).complete(
                org.mockito.ArgumentMatchers.eq(claimedJob),
                resultCaptor.capture()
        );

        ExecutionResult result = resultCaptor.getValue();

        assertThat(result.status())
                .isEqualTo(ExecutionStatus.FAILED);

        assertThat(result.finishedAt())
                .isEqualTo(CURRENT_TIME);

        assertThat(result.errorType())
                .contains("IllegalStateException");

        assertThat(result.errorMessage())
                .isEqualTo("Handler failed");
    }

    @Test
    void shouldRecordFailureWhenJobTypeIsUnknown() {
        ClaimedJob claimedJob = createClaimedJob();

        when(jobHandlerRegistry.getRequiredHandler("PRINT_MESSAGE"))
                .thenThrow(
                        new UnknownJobTypeException("PRINT_MESSAGE")
                );

        processor.process(claimedJob);

        ArgumentCaptor<ExecutionResult> resultCaptor =
                ArgumentCaptor.forClass(ExecutionResult.class);

        verify(completionService).complete(
                org.mockito.ArgumentMatchers.eq(claimedJob),
                resultCaptor.capture()
        );

        ExecutionResult result = resultCaptor.getValue();

        assertThat(result.status())
                .isEqualTo(ExecutionStatus.FAILED);

        assertThat(result.errorType())
                .contains("UnknownJobTypeException");
    }

    private ClaimedJob createClaimedJob() {
        Job job = new Job(
                JOB_ID,
                "default",
                "PRINT_MESSAGE",
                "{\"message\":\"Hello ChronosQ\"}",
                JobStatus.RUNNING,
                0,
                CURRENT_TIME,
                ScheduleType.IMMEDIATE,
                null,
                1,
                3,
                null,
                WORKER_ID,
                CURRENT_TIME.plusSeconds(60),
                30,
                CURRENT_TIME,
                CURRENT_TIME,
                null,
                1L
        );

        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                JOB_ID,
                WORKER_ID,
                1,
                ExecutionStatus.RUNNING,
                CURRENT_TIME,
                null,
                null,
                null,
                null
        );

        return new ClaimedJob(job, execution);
    }
}