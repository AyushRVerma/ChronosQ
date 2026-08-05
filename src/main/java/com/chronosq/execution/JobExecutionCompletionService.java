package com.chronosq.execution;

import java.util.Objects;

import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.repository.JobRepository;
import com.chronosq.worker.ClaimedJob;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// This class handles the atomic completion process when a worker finishes running a job!
//When a job completes (either successfully or with a failure that shouldn't
// be retried), JobExecutionCompletionService.complete(...) updates both database tables (job_executions AND jobs) inside a single atomic @Transactional transaction.
public class JobExecutionCompletionService {

    private final JobRepository jobRepository;

    private final JobExecutionRepository
            jobExecutionRepository;


    @Transactional
    public void complete(
            ClaimedJob claimedJob,
            ExecutionResult executionResult
    ) {

        Objects.requireNonNull(
                claimedJob,
                "claimedJob must not be null"
        );

        Objects.requireNonNull(
                executionResult,
                "executionResult must not be null"
        );

        boolean executionUpdated = jobExecutionRepository
                .finalizeExecution(
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

        JobStatus finalJobStatus = calculateFinalJobStatus(executionResult);

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

    private JobStatus calculateFinalJobStatus(
            ExecutionResult executionResult
    ) {

        if (executionResult.status()
                == ExecutionStatus.SUCCEEDED) {

            return JobStatus.SUCCEEDED;
        }

        return JobStatus.DEAD_LETTERED;
    }
}