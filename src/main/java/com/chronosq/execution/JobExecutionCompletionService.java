package com.chronosq.execution;

import java.util.Objects;

import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.repository.JobRepository;
import com.chronosq.recovery.RetryDecision;
import com.chronosq.recovery.RetryDecisionService;
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
}