package com.chronosq.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.worker.ClaimedJob;

@ExtendWith(MockitoExtension.class)
class JobExecutionDispatcherTest {

    private static final UUID JOB_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID EXECUTION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String WORKER_ID = "worker-1";

    private static final Instant CURRENT_TIME =
            Instant.parse("2026-08-05T10:00:00Z");

    @Mock
    private ThreadPoolTaskExecutor taskExecutor;

    @Mock
    private JobExecutionProcessor processor;

    @Mock
    private JobExecutionCompletionService completionService;

    private JobExecutionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                CURRENT_TIME,
                ZoneOffset.UTC
        );

        dispatcher = new JobExecutionDispatcher(
                taskExecutor,
                processor,
                completionService,
                clock
        );
    }

    @Test
    void shouldDispatchClaimedJobToExecutionThreadPool() {
        ClaimedJob claimedJob = createClaimedJob();

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        })
                .when(taskExecutor)
                .execute(any(Runnable.class));

        int dispatchedCount = dispatcher.dispatch(
                List.of(claimedJob)
        );

        assertThat(dispatchedCount).isEqualTo(1);

        verify(taskExecutor).execute(any(Runnable.class));
        verify(processor).process(claimedJob);
    }

    @Test
    void shouldReturnZeroWhenThereAreNoJobs() {
        int dispatchedCount = dispatcher.dispatch(List.of());

        assertThat(dispatchedCount).isZero();
    }

    @Test
    void shouldFinalizeJobWhenThreadPoolRejectsTask() {
        ClaimedJob claimedJob = createClaimedJob();

        doThrow(new TaskRejectedException("Execution queue is full"))
                .when(taskExecutor)
                .execute(any(Runnable.class));

        int dispatchedCount = dispatcher.dispatch(
                List.of(claimedJob)
        );

        assertThat(dispatchedCount).isZero();

        ArgumentCaptor<ExecutionResult> resultCaptor =
                ArgumentCaptor.forClass(ExecutionResult.class);

        verify(completionService).complete(
                eq(claimedJob),
                resultCaptor.capture()
        );

        ExecutionResult result = resultCaptor.getValue();

        assertThat(result.status())
                .isEqualTo(ExecutionStatus.FAILED);

        assertThat(result.finishedAt())
                .isEqualTo(CURRENT_TIME);

        assertThat(result.errorType())
                .isEqualTo("TASK_REJECTED");

        assertThat(result.errorMessage())
                .contains("Execution queue is full");
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