package com.chronosq.execution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.MissedExecutionPolicy;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;
import com.chronosq.recovery.RetryDecision;
import com.chronosq.recovery.RetryDecisionService;
import com.chronosq.scheduler.JobScheduleCalculator;
import com.chronosq.worker.ClaimedJob;

@ExtendWith(MockitoExtension.class)
class JobExecutionCompletionServiceTest {

    private static final UUID JOB_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID EXECUTION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String WORKER_ID = "worker-1";

    private static final Instant STARTED_AT =
            Instant.parse("2026-08-06T10:00:00Z");

    private static final Instant FINISHED_AT =
            Instant.parse("2026-08-06T10:00:01Z");

    private static final Instant RETRY_AT =
            Instant.parse("2026-08-06T10:00:05Z");

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository jobExecutionRepository;

    @Mock
    private RetryDecisionService retryDecisionService;

    @Mock
    private JobScheduleCalculator jobScheduleCalculator;

    private JobExecutionCompletionService completionService;

    @BeforeEach
    void setUp() {
        completionService = new JobExecutionCompletionService(
                jobRepository,
                jobExecutionRepository,
                retryDecisionService,
                jobScheduleCalculator
        );
    }

    @Test
    void shouldCompleteSuccessfulExecutionAndJob() {
        ClaimedJob claimedJob = createClaimedJob();

        ExecutionResult result = successfulResult();

        when(jobExecutionRepository.finalizeExecution(
                EXECUTION_ID,
                WORKER_ID,
                result
        )).thenReturn(true);

        when(jobRepository.finishRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.SUCCEEDED,
                FINISHED_AT,
                FINISHED_AT,
                1L
        )).thenReturn(true);

        completionService.complete(
                claimedJob,
                result
        );

        verify(jobRepository).finishRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.SUCCEEDED,
                FINISHED_AT,
                FINISHED_AT,
                1L
        );
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void shouldScheduleRetryForRetryableFailure() {
        ClaimedJob claimedJob = createClaimedJob();

        ExecutionResult result = failedResult();

        RuntimeException failure =
                new RuntimeException(
                        "Temporary service problem"
                );

        when(jobExecutionRepository.finalizeExecution(
                EXECUTION_ID,
                WORKER_ID,
                result
        )).thenReturn(true);

        when(retryDecisionService.decide(
                claimedJob.job(),
                failure,
                FINISHED_AT
        )).thenReturn(
                RetryDecision.retryAt(RETRY_AT)
        );

        when(jobRepository.retryRunningJob(
                JOB_ID,
                WORKER_ID,
                RETRY_AT,
                FINISHED_AT,
                1L
        )).thenReturn(true);

        completionService.complete(
                claimedJob,
                result,
                failure
        );

        verify(jobRepository).retryRunningJob(
                JOB_ID,
                WORKER_ID,
                RETRY_AT,
                FINISHED_AT,
                1L
        );

        verify(jobRepository, never()).finishRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.DEAD_LETTERED,
                FINISHED_AT,
                FINISHED_AT,
                1L
        );
    }

    @Test
    void shouldCreateNextOccurrenceForSuccessfulRecurringJob() {
        ClaimedJob claimedJob = createClaimedJob(
                ScheduleType.FIXED_INTERVAL,
                300L
        );
        ExecutionResult result = successfulResult();
        Instant nextAvailableAt =
                Instant.parse("2026-08-06T10:05:01Z");

        when(jobExecutionRepository.finalizeExecution(
                EXECUTION_ID, WORKER_ID, result
        )).thenReturn(true);
        when(jobRepository.finishRunningJob(
                JOB_ID, WORKER_ID, JobStatus.SUCCEEDED,
                FINISHED_AT, FINISHED_AT, 1L
        )).thenReturn(true);
        when(jobScheduleCalculator.calculateNextFixedInterval(
                FINISHED_AT, 300L
        )).thenReturn(nextAvailableAt);
        when(jobRepository.save(any(Job.class))).thenReturn(true);

        completionService.complete(claimedJob, result);

        ArgumentCaptor<Job> nextJob =
                ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(nextJob.capture());

        org.assertj.core.api.Assertions.assertThat(nextJob.getValue())
                .satisfies(job -> {
                    org.assertj.core.api.Assertions.assertThat(job.status())
                            .isEqualTo(JobStatus.SCHEDULED);
                    org.assertj.core.api.Assertions.assertThat(job.availableAt())
                            .isEqualTo(nextAvailableAt);
                    org.assertj.core.api.Assertions.assertThat(job.attemptCount())
                            .isZero();
                    org.assertj.core.api.Assertions.assertThat(job.idempotencyKey())
                            .isNull();
                });
    }

    @Test
    void shouldCatchUpEveryMissedCronOccurrence() {
        Instant scheduledAt = Instant.parse("2026-08-06T09:55:00Z");
        Instant nextMissedAt = Instant.parse("2026-08-06T09:56:00Z");
        ClaimedJob claimedJob = createCronClaimedJob(
                MissedExecutionPolicy.CATCH_UP_ALL,
                scheduledAt
        );
        ExecutionResult result = successfulResult();
        stubSuccessfulFinalization(result);
        when(jobScheduleCalculator.calculateNextCron(
                scheduledAt, "0 * * * * *", "UTC"
        )).thenReturn(nextMissedAt);
        when(jobRepository.save(any(Job.class))).thenReturn(true);

        completionService.complete(claimedJob, result);

        ArgumentCaptor<Job> nextJob = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(nextJob.capture());
        org.assertj.core.api.Assertions.assertThat(nextJob.getValue().status())
                .isEqualTo(JobStatus.READY);
        org.assertj.core.api.Assertions.assertThat(
                nextJob.getValue().availableAt()
        ).isEqualTo(nextMissedAt);
    }

    @Test
    void shouldRunOnlyOnceThenResumeCronAfterCompletion() {
        ClaimedJob claimedJob = createCronClaimedJob(
                MissedExecutionPolicy.RUN_ONCE_IMMEDIATELY,
                Instant.parse("2026-08-06T09:55:00Z")
        );
        ExecutionResult result = successfulResult();
        Instant nextFutureAt = Instant.parse("2026-08-06T10:01:00Z");
        stubSuccessfulFinalization(result);
        when(jobScheduleCalculator.calculateNextCron(
                FINISHED_AT, "0 * * * * *", "UTC"
        )).thenReturn(nextFutureAt);
        when(jobRepository.save(any(Job.class))).thenReturn(true);

        completionService.complete(claimedJob, result);

        ArgumentCaptor<Job> nextJob = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(nextJob.capture());
        org.assertj.core.api.Assertions.assertThat(nextJob.getValue().status())
                .isEqualTo(JobStatus.SCHEDULED);
        org.assertj.core.api.Assertions.assertThat(
                nextJob.getValue().availableAt()
        ).isEqualTo(nextFutureAt);
    }

    @Test
    void shouldDeadLetterPermanentFailure() {
        ClaimedJob claimedJob = createClaimedJob();

        ExecutionResult result = failedResult();

        IllegalArgumentException failure =
                new IllegalArgumentException(
                        "Invalid payload"
                );

        when(jobExecutionRepository.finalizeExecution(
                EXECUTION_ID,
                WORKER_ID,
                result
        )).thenReturn(true);

        when(retryDecisionService.decide(
                claimedJob.job(),
                failure,
                FINISHED_AT
        )).thenReturn(
                RetryDecision.deadLetter()
        );

        when(jobRepository.finishRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.DEAD_LETTERED,
                FINISHED_AT,
                FINISHED_AT,
                1L
        )).thenReturn(true);

        completionService.complete(
                claimedJob,
                result,
                failure
        );

        verify(jobRepository).finishRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.DEAD_LETTERED,
                FINISHED_AT,
                FINISHED_AT,
                1L
        );
    }

    @Test
    void shouldThrowWhenExecutionCannotBeFinalized() {
        ClaimedJob claimedJob = createClaimedJob();

        ExecutionResult result = successfulResult();

        when(jobExecutionRepository.finalizeExecution(
                EXECUTION_ID,
                WORKER_ID,
                result
        )).thenReturn(false);

        assertThatThrownBy(
                () -> completionService.complete(
                        claimedJob,
                        result
                )
        )
                .isInstanceOf(
                        ExecutionCompletionConflictException.class
                );

        verify(jobRepository, never()).finishRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.SUCCEEDED,
                FINISHED_AT,
                FINISHED_AT,
                1L
        );
    }

    private ExecutionResult successfulResult() {
        return new ExecutionResult(
                ExecutionStatus.SUCCEEDED,
                FINISHED_AT,
                1_000,
                null,
                null
        );
    }

    private ExecutionResult failedResult() {
        return new ExecutionResult(
                ExecutionStatus.FAILED,
                FINISHED_AT,
                1_000,
                "RuntimeException",
                "Temporary service problem"
        );
    }

    private ClaimedJob createClaimedJob() {
        return createClaimedJob(ScheduleType.IMMEDIATE, null);
    }

    @Test
    void shouldContinueCronScheduleAfterPermanentFailure() {
        ClaimedJob claimedJob = createCronClaimedJob(
                MissedExecutionPolicy.RUN_ONCE_IMMEDIATELY,
                Instant.parse("2026-08-06T09:55:00Z")
        );
        ExecutionResult result = failedResult();
        RuntimeException failure = new RuntimeException("permanent");
        Instant nextFutureAt = Instant.parse("2026-08-06T10:01:00Z");

        when(jobExecutionRepository.finalizeExecution(
                EXECUTION_ID, WORKER_ID, result
        )).thenReturn(true);
        when(retryDecisionService.decide(
                claimedJob.job(), failure, FINISHED_AT
        )).thenReturn(RetryDecision.deadLetter());
        when(jobRepository.finishRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.DEAD_LETTERED,
                FINISHED_AT,
                FINISHED_AT,
                1L
        )).thenReturn(true);
        when(jobScheduleCalculator.calculateNextCron(
                FINISHED_AT, "0 * * * * *", "UTC"
        )).thenReturn(nextFutureAt);
        when(jobRepository.save(any(Job.class))).thenReturn(true);

        completionService.complete(claimedJob, result, failure);

        ArgumentCaptor<Job> nextJob = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(nextJob.capture());
        org.assertj.core.api.Assertions.assertThat(nextJob.getValue().status())
                .isEqualTo(JobStatus.SCHEDULED);
        org.assertj.core.api.Assertions.assertThat(
                nextJob.getValue().availableAt()
        ).isEqualTo(nextFutureAt);
    }

    private ClaimedJob createClaimedJob(
            ScheduleType scheduleType,
            Long intervalSeconds
    ) {
        Job job = new Job(
                JOB_ID,
                "default",
                "PRINT_MESSAGE",
                "{\"message\":\"Hello\"}",
                JobStatus.RUNNING,
                0,
                STARTED_AT,
                scheduleType,
                intervalSeconds,
                1,
                3,
                null,
                WORKER_ID,
                STARTED_AT.plusSeconds(60),
                30,
                STARTED_AT,
                STARTED_AT,
                null,
                1L
        );

        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                JOB_ID,
                WORKER_ID,
                1,
                ExecutionStatus.RUNNING,
                STARTED_AT,
                null,
                null,
                null,
                null
        );

        return new ClaimedJob(job, execution);
    }

    private ClaimedJob createCronClaimedJob(
            MissedExecutionPolicy policy,
            Instant availableAt
    ) {
        Job job = new Job(
                JOB_ID,
                "default",
                "PRINT_MESSAGE",
                "{\"message\":\"Hello\"}",
                JobStatus.RUNNING,
                0,
                availableAt,
                ScheduleType.CRON,
                null,
                "0 * * * * *",
                "UTC",
                policy,
                1,
                3,
                null,
                WORKER_ID,
                FINISHED_AT.plusSeconds(60),
                30,
                availableAt,
                availableAt,
                null,
                1L
        );
        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                JOB_ID,
                WORKER_ID,
                1,
                ExecutionStatus.RUNNING,
                STARTED_AT,
                null,
                null,
                null,
                null
        );
        return new ClaimedJob(job, execution);
    }

    private void stubSuccessfulFinalization(ExecutionResult result) {
        when(jobExecutionRepository.finalizeExecution(
                EXECUTION_ID, WORKER_ID, result
        )).thenReturn(true);
        when(jobRepository.finishRunningJob(
                JOB_ID,
                WORKER_ID,
                JobStatus.SUCCEEDED,
                FINISHED_AT,
                FINISHED_AT,
                1L
        )).thenReturn(true);
    }
}
