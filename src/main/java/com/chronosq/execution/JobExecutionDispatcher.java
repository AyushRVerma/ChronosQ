package com.chronosq.execution;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.chronosq.worker.ClaimedJob;
import com.chronosq.worker.JobClaimService;

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

    private final JobClaimService jobClaimService;

    private final Clock clock;
    private final ScheduledExecutorService jobTimeoutScheduler;
    private final ExecutorService jobTimeoutCompletionExecutor;

    public JobExecutionDispatcher(

            ThreadPoolTaskExecutor jobExecutionTaskExecutor,

            JobExecutionProcessor jobExecutionProcessor,

            JobClaimService jobClaimService,
            Clock clock,
            @Qualifier("jobTimeoutScheduler")
            ScheduledExecutorService jobTimeoutScheduler,
            @Qualifier("jobTimeoutCompletionExecutor")
            ExecutorService jobTimeoutCompletionExecutor
    ) {
        this.jobExecutionTaskExecutor = jobExecutionTaskExecutor;

        this.jobExecutionProcessor = jobExecutionProcessor;

        this.jobClaimService = jobClaimService;

        this.clock = clock;
        this.jobTimeoutScheduler = jobTimeoutScheduler;
        this.jobTimeoutCompletionExecutor =
                jobTimeoutCompletionExecutor;
    }

    // Handing jobs to threads
    public int dispatch(List<ClaimedJob> claimedJobs) {

        Objects.requireNonNull(claimedJobs, "claimedJobs must not be null");

        int dispatchedCount = 0;

        for (ClaimedJob claimedJob : claimedJobs) {

            try {
                // Hand off this job to a background worker thread to process
                AtomicBoolean completionClaimed =
                        new AtomicBoolean(false);
                AtomicReference<Future<?>> executionFuture =
                        new AtomicReference<>();
                FutureTask<Void> executionTask =
                        new FutureTask<>(() -> {
                            ScheduledFuture<?> timeoutTask =
                                    jobTimeoutScheduler.schedule(
                                            () -> handleTimeout(
                                                    claimedJob,
                                                    completionClaimed,
                                                    executionFuture.get()
                                            ),
                                            claimedJob.job().timeoutSeconds(),
                                            TimeUnit.SECONDS
                                    );
                            try {
                                jobExecutionProcessor.process(
                                        claimedJob,
                                        completionClaimed
                                );
                            } catch (RuntimeException exception) {
                                log.error(
                                        "Job execution infrastructure failed. jobId={}, executionId={}",
                                        claimedJob.job().id(),
                                        claimedJob.execution().id(),
                                        exception
                                );
                                throw exception;
                            } finally {
                                timeoutTask.cancel(false);
                            }
                            return null;
                        });
                executionFuture.set(executionTask);
                jobExecutionTaskExecutor.execute(executionTask);

                dispatchedCount++;

            } catch (TaskRejectedException exception) {

//                If the thread pool is FULL and rejected the job:
                handleRejectedTask(claimedJob, exception
                );
            }
        }

        return dispatchedCount;
    }

    public int availableCapacity() {
        ThreadPoolExecutor executor =
                jobExecutionTaskExecutor.getThreadPoolExecutor();
        if (executor.isShutdown()) {
            return 0;
        }

        // The pool has a fixed maximum size. Slots consist of workers that
        // have not yet been created plus free places in its bounded queue.
        int uncreatedWorkers = Math.max(0,
                executor.getMaximumPoolSize() - executor.getPoolSize());
        return uncreatedWorkers + executor.getQueue().remainingCapacity();
    }

    private void handleTimeout(
            ClaimedJob claimedJob,
            AtomicBoolean completionClaimed,
            Future<?> executionFuture
    ) {
        if (executionFuture.isDone()) {
            return;
        }

        try {
            Runnable timeoutFinalization =
                    jobExecutionProcessor.claimTimeout(
                    claimedJob,
                    completionClaimed,
                    () -> executionFuture.cancel(true)
            );

            if (timeoutFinalization == null) {
                return;
            }

            jobTimeoutCompletionExecutor.execute(
                    () -> finalizeTimeout(
                            claimedJob,
                            timeoutFinalization
                    )
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Could not finalize timed-out job. jobId={}, executionId={}",
                    claimedJob.job().id(),
                    claimedJob.execution().id(),
                    exception
            );
        }
    }

    private void finalizeTimeout(
            ClaimedJob claimedJob,
            Runnable timeoutFinalization
    ) {
        try {
            timeoutFinalization.run();
            log.warn(
                    "Job execution timed out. jobId={}, executionId={}",
                    claimedJob.job().id(),
                    claimedJob.execution().id()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Could not finalize timed-out job. jobId={}, executionId={}",
                    claimedJob.job().id(),
                    claimedJob.execution().id(),
                    exception
            );
        }
    }

    //What if the thread pool rejects it?
    private void handleRejectedTask(ClaimedJob claimedJob, TaskRejectedException exception) {

        // No handler ran: restore READY and the original attempt count.
        // This also removes the never-started execution record atomically.
        jobClaimService.releaseUnstartedJob(claimedJob, clock.instant());

        log.warn(
                "Job execution task was rejected without consuming an attempt. "
                        + "jobId={}, executionId={}, reason={}",
                claimedJob.job().id(),
                claimedJob.execution().id(),
                exception.getMessage()
        );
    }
}
