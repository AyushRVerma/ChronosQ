package com.chronosq.execution;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.MissedExecutionPolicy;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;
import com.chronosq.recovery.RetryDecision;
import com.chronosq.recovery.RetryDecisionService;
import com.chronosq.scheduler.JobScheduleCalculator;
import com.chronosq.worker.ClaimedJob;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobExecutionCompletionService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final RetryDecisionService retryDecisionService;
    private final JobScheduleCalculator jobScheduleCalculator;

    @Transactional
    public void complete(
            ClaimedJob claimedJob,
            ExecutionResult executionResult
    ) {
        Throwable fallbackFailure = executionResult.status() == ExecutionStatus.SUCCEEDED
                        ? null
                        : new IllegalStateException(
                        "Execution failed without "
                                + "an original exception"
                );

        complete(
                claimedJob,
                executionResult,
                fallbackFailure
        );
    }

    @Transactional
    public void complete(
            ClaimedJob claimedJob,
            ExecutionResult executionResult,
            Throwable failure
    ) {
        Objects.requireNonNull(
                claimedJob,
                "claimedJob must not be null"
        );

        Objects.requireNonNull(
                executionResult,
                "executionResult must not be null"
        );

        boolean executionUpdated =
                jobExecutionRepository.finalizeExecution(
                        claimedJob.execution().id(),
                        claimedJob.execution().workerId(),
                        executionResult
                );

        if (!executionUpdated) {
            throw new ExecutionCompletionConflictException(
                    claimedJob.job().id(),
                    claimedJob.execution().id()
            );
        }

        if (executionResult.status()
                == ExecutionStatus.SUCCEEDED) {

            finishJob(
                    claimedJob,
                    JobStatus.SUCCEEDED,
                    executionResult
            );

            scheduleNextOccurrenceIfRecurring(
                    claimedJob.job(),
                    executionResult.finishedAt()
            );

            return;
        }

        Throwable actualFailure = Objects.requireNonNull(
                failure,
                "failure must not be null for failed execution"
        );

        RetryDecision retryDecision =
                retryDecisionService.decide(
                        claimedJob.job(),
                        actualFailure,
                        executionResult.finishedAt()
                );

        if (retryDecision.shouldRetry()) {
            boolean jobUpdated =
                    jobRepository.retryRunningJob(
                            claimedJob.job().id(),
                            claimedJob.job().lockedBy(),
                            retryDecision.nextRetryAt(),
                            executionResult.finishedAt(),
                            claimedJob.job().version()
                    );

            if (!jobUpdated) {
                throw new ExecutionCompletionConflictException(
                        claimedJob.job().id(),
                        claimedJob.execution().id()
                );
            }

            return;
        }

        finishJob(
                claimedJob,
                JobStatus.DEAD_LETTERED,
                executionResult
        );

        if (claimedJob.job().scheduleType() == ScheduleType.CRON) {
            scheduleNextOccurrenceIfRecurring(
                    claimedJob.job(),
                    executionResult.finishedAt()
            );
        }
    }

    private void finishJob(
            ClaimedJob claimedJob,
            JobStatus finalJobStatus,
            ExecutionResult executionResult
    ) {
        boolean jobUpdated =
                jobRepository.finishRunningJob(
                        claimedJob.job().id(),
                        claimedJob.job().lockedBy(),
                        finalJobStatus,
                        executionResult.finishedAt(),
                        executionResult.finishedAt(),
                        claimedJob.job().version()
                );

        if (!jobUpdated) {
            throw new ExecutionCompletionConflictException(
                    claimedJob.job().id(),
                    claimedJob.execution().id()
            );
        }
    }

    private void scheduleNextOccurrenceIfRecurring(
            Job completedJob,
            Instant completedAt
    ) {
        if (completedJob.scheduleType() != ScheduleType.FIXED_INTERVAL
                && completedJob.scheduleType() != ScheduleType.CRON) {
            return;
        }

        Instant nextAvailableAt;
        JobStatus nextStatus = JobStatus.SCHEDULED;

        if (completedJob.scheduleType() == ScheduleType.FIXED_INTERVAL) {
            long intervalSeconds = Objects.requireNonNull(
                    completedJob.intervalSeconds(),
                    "Recurring job interval must not be null"
            );
            nextAvailableAt =
                    jobScheduleCalculator.calculateNextFixedInterval(
                            completedAt,
                            intervalSeconds
                    );
        } else {
            Instant calculationBase =
                    completedJob.missedExecutionPolicy()
                                    == MissedExecutionPolicy.CATCH_UP_ALL
                            ? completedJob.availableAt()
                            : completedAt;

            nextAvailableAt = jobScheduleCalculator.calculateNextCron(
                    calculationBase,
                    completedJob.cronExpression(),
                    completedJob.cronTimeZone()
            );
            if (!nextAvailableAt.isAfter(completedAt)) {
                nextStatus = JobStatus.READY;
            }
        }

        Job nextOccurrence = new Job(
                UUID.randomUUID(),
                completedJob.queueName(),
                completedJob.jobType(),
                completedJob.payload(),
                nextStatus,
                completedJob.priority(),
                nextAvailableAt,
                completedJob.scheduleType(),
                completedJob.intervalSeconds(),
                completedJob.cronExpression(),
                completedJob.cronTimeZone(),
                completedJob.missedExecutionPolicy(),
                0,
                completedJob.maxAttempts(),
                null,
                null,
                null,
                completedJob.timeoutSeconds(),
                completedAt,
                completedAt,
                null,
                0L
        );

        if (!jobRepository.save(nextOccurrence)) {
            throw new IllegalStateException(
                    "Could not persist the next occurrence for job "
                            + completedJob.id()
            );
        }
    }
}
