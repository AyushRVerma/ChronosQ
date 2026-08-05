package com.chronosq.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronosq.execution.ExecutionStatus;
import com.chronosq.execution.JobExecution;
import com.chronosq.execution.JobExecutionDispatcher;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

@ExtendWith(MockitoExtension.class)
class WorkerPollerTest {

    private static final Instant CURRENT_TIME =
            Instant.parse("2026-08-05T10:00:00Z");

    @Mock
    private JobClaimService jobClaimService;

    @Mock
    private JobExecutionDispatcher jobExecutionDispatcher;

    private WorkerPoller workerPoller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                CURRENT_TIME,
                ZoneOffset.UTC
        );

        workerPoller = new WorkerPoller(
                jobClaimService,
                jobExecutionDispatcher,
                clock
        );
    }

    @Test
    void shouldClaimAndDispatchAvailableJobs() {
        ClaimedJob claimedJob = createClaimedJob();

        when(jobClaimService.claimAvailableJobs(CURRENT_TIME))
                .thenReturn(List.of(claimedJob));

        when(jobExecutionDispatcher.dispatch(List.of(claimedJob)))
                .thenReturn(1);

        workerPoller.pollAndDispatch();

        verify(jobClaimService)
                .claimAvailableJobs(CURRENT_TIME);

        verify(jobExecutionDispatcher)
                .dispatch(List.of(claimedJob));
    }

    @Test
    void shouldPassEmptyListToDispatcherWhenNoJobsAreAvailable() {
        when(jobClaimService.claimAvailableJobs(CURRENT_TIME))
                .thenReturn(List.of());

        when(jobExecutionDispatcher.dispatch(List.of()))
                .thenReturn(0);

        workerPoller.pollAndDispatch();

        verify(jobExecutionDispatcher)
                .dispatch(List.of());
    }

    @Test
    void shouldNotCrashSchedulerWhenClaimingFails() {
        when(jobClaimService.claimAvailableJobs(CURRENT_TIME))
                .thenThrow(
                        new IllegalStateException(
                                "Database temporarily unavailable"
                        )
                );

        assertThatCode(workerPoller::pollAndDispatch)
                .doesNotThrowAnyException();

        verify(jobExecutionDispatcher, never())
                .dispatch(org.mockito.ArgumentMatchers.anyList());
    }

    private ClaimedJob createClaimedJob() {
        UUID jobId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        Job job = new Job(
                jobId,
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
                "worker-1",
                CURRENT_TIME.plusSeconds(60),
                30,
                CURRENT_TIME,
                CURRENT_TIME,
                null,
                1L
        );

        JobExecution execution = new JobExecution(
                UUID.fromString(
                        "22222222-2222-2222-2222-222222222222"
                ),
                jobId,
                "worker-1",
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