package com.chronosq.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.worker.ClaimedJob;
import com.chronosq.worker.JobClaimService;
import com.chronosq.configuration.ExecutionConfiguration;
import com.chronosq.configuration.ExecutionProperties;

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
    private JobClaimService jobClaimService;

    @Mock
    private ScheduledExecutorService timeoutScheduler;

    @Mock
    private ScheduledFuture<Object> scheduledFuture;

    @Mock
    private ExecutorService timeoutCompletionExecutor;

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
                jobClaimService,
                clock,
                timeoutScheduler,
                timeoutCompletionExecutor
        );
    }

    @Test
    void shouldDispatchClaimedJobToExecutionThreadPool() {
        ClaimedJob claimedJob = createClaimedJob();

        doReturn(scheduledFuture)
                .when(timeoutScheduler)
                .schedule(
                        any(Runnable.class),
                        eq(30L),
                        eq(TimeUnit.SECONDS)
                );
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return null;
                }).when(taskExecutor).execute(any(Runnable.class));

        int dispatchedCount = dispatcher.dispatch(
                List.of(claimedJob)
        );

        assertThat(dispatchedCount).isEqualTo(1);

        verify(taskExecutor).execute(any(Runnable.class));
        verify(processor).process(
                eq(claimedJob),
                any(java.util.concurrent.atomic.AtomicBoolean.class)
        );
        verify(timeoutScheduler).schedule(
                any(Runnable.class),
                eq(30L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldReturnZeroWhenThereAreNoJobs() {
        int dispatchedCount = dispatcher.dispatch(List.of());

        assertThat(dispatchedCount).isZero();
    }

    @Test
    void shouldReleaseJobWithoutConsumingAttemptWhenThreadPoolRejectsTask() {

        ClaimedJob claimedJob =
                createClaimedJob();

        TaskRejectedException rejectionException =
                new TaskRejectedException(
                        "Execution queue is full"
                );

        doThrow(rejectionException)
                .when(taskExecutor)
                .execute(any(Runnable.class));

        int dispatchedCount =
                dispatcher.dispatch(
                        List.of(claimedJob)
                );

        assertThat(dispatchedCount)
                .isZero();

        verify(jobClaimService).releaseUnstartedJob(claimedJob, CURRENT_TIME);
        verify(processor, never()).process(
                eq(claimedJob),
                any(java.util.concurrent.atomic.AtomicBoolean.class)
        );
    }

    @Test
    void shouldReportZeroCapacityWhenWorkersAndQueueAreFull() throws Exception {
        ThreadPoolTaskExecutor realPool = new ExecutionConfiguration()
                .jobExecutionTaskExecutor(new ExecutionProperties(1000, 1, 1));
        realPool.initialize();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            JobExecutionDispatcher realDispatcher = new JobExecutionDispatcher(
                    realPool, processor, jobClaimService, Clock.fixed(
                    CURRENT_TIME, ZoneOffset.UTC), timeoutScheduler,
                    timeoutCompletionExecutor);

            assertThat(realDispatcher.availableCapacity()).isEqualTo(2);
            realPool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            realPool.execute(() -> { });

            assertThat(realDispatcher.availableCapacity()).isZero();
        } finally {
            release.countDown();
            realPool.shutdown();
        }
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
