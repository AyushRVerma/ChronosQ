package com.chronosq.execution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.chronosq.worker.ClaimedJob;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.core.task.TaskRejectedException;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.springframework.stereotype.Component;

@Component
@Slf4j
//We are connecting ThreadPoolTaskExecutor and JobExecutionProcessor together!
//
//JobExecutionDispatcher takes a list of ClaimedJob records (claimed from PostgreSQL) and submits
// them to the multithreaded jobExecutionTaskExecutor pool for parallel processing.
public class JobExecutionDispatcher {



    private final ThreadPoolTaskExecutor
            jobExecutionTaskExecutor;

    private final JobExecutionProcessor
            jobExecutionProcessor;

    private final JobExecutionCompletionService
            jobExecutionCompletionService;

    private final Clock clock;

    public JobExecutionDispatcher(

            ThreadPoolTaskExecutor jobExecutionTaskExecutor,

            JobExecutionProcessor jobExecutionProcessor,

            JobExecutionCompletionService jobExecutionCompletionService,

            Clock clock
    ) {
        this.jobExecutionTaskExecutor = jobExecutionTaskExecutor;

        this.jobExecutionProcessor = jobExecutionProcessor;

        this.jobExecutionCompletionService = jobExecutionCompletionService;

        this.clock = clock;
    }

    // Handing jobs to threads
    public int dispatch(List<ClaimedJob> claimedJobs) {

        Objects.requireNonNull(claimedJobs, "claimedJobs must not be null");

        int dispatchedCount = 0;

        for (ClaimedJob claimedJob : claimedJobs) {

            try {
                // Hand off this job to a background worker thread to process
                jobExecutionTaskExecutor.execute(() -> jobExecutionProcessor.process(claimedJob)
                );

                dispatchedCount++;

            } catch (TaskRejectedException exception) {

//                If the thread pool is FULL and rejected the job:
                handleRejectedTask(claimedJob, exception
                );
            }
        }

        return dispatchedCount;
    }

    //What if the thread pool rejects it?
    private void handleRejectedTask(ClaimedJob claimedJob, TaskRejectedException exception) {

        Instant finishedAt = clock.instant();

        long durationMs = Duration.between(
                        claimedJob.execution().startedAt(),
                        finishedAt).toMillis();

        ExecutionResult result =ExecutionResult.failed(
                        ExecutionStatus.FAILED,
                        finishedAt,
                        Math.max(0L, durationMs),
                        "TASK_REJECTED",
                        exception.getMessage()
                );

        // Safely tell DB: "This job attempt failed because server memory was full"
        jobExecutionCompletionService.complete(claimedJob, result, exception);

        log.warn(
                """
                Job execution task was rejected. \
                jobId={}, executionId={}
                """,
                claimedJob.job().id(),
                claimedJob.execution().id(),
                exception
        );
    }
}