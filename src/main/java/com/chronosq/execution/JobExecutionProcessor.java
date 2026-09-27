package com.chronosq.execution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.chronosq.handler.JobHandler;
import com.chronosq.handler.JobHandlerRegistry;
import com.chronosq.metrics.JobExecutionObserver;
import com.chronosq.metrics.JobLogContext;
import com.chronosq.worker.ClaimedJob;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor

//JobExecutionProcessor is the component that actually runs a claimed job.
// It looks up the right business handler from JobHandlerRegistry, executes it,
// measures the run time, and routes the success or failure outcome to JobExecutionCompletionService.
public class JobExecutionProcessor {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    JobExecutionProcessor.class
            );

    private final JobHandlerRegistry jobHandlerRegistry;

    private final JobExecutionCompletionService jobExecutionCompletionService;

    private final JobExecutionObserver jobExecutionObserver;
    private final Clock clock;


    public void process(ClaimedJob claimedJob) {

        process(claimedJob, new AtomicBoolean());
    }

    public void process(
            ClaimedJob claimedJob,
            AtomicBoolean completionClaimed
    ) {

        Objects.requireNonNull(
                claimedJob,
                "claimedJob must not be null"
        );
        Objects.requireNonNull(
                completionClaimed,
                "completionClaimed must not be null"
        );

        try (JobLogContext ignored =
                     jobExecutionObserver.openLogContext(
                             claimedJob
                     )) {

            logger.info("Job execution started");

            try {
                JobHandler handler =
                        jobHandlerRegistry.getRequiredHandler(
                                claimedJob.job().jobType()
                        );
                handler.execute(claimedJob.job());
            } catch (Exception exception) {
                completeWithFailure(
                        claimedJob,
                        exception,
                        completionClaimed
                );
                return;
            }

            // Completion persistence is deliberately outside the handler
            // catch block. A database or transaction failure here must not
            // be misclassified as a handler failure and retried as though
            // the external side effect had failed.
            completeSuccessfully(
                    claimedJob,
                    completionClaimed
            );
        }

    }
    public boolean timeOut(
            ClaimedJob claimedJob,
            AtomicBoolean completionClaimed
    ) {
        return timeOut(
                claimedJob,
                completionClaimed,
                () -> { }
        );
    }

    public boolean timeOut(
            ClaimedJob claimedJob,
            AtomicBoolean completionClaimed,
            Runnable interruptExecution
    ) {
        Runnable timeoutFinalization = claimTimeout(
                claimedJob,
                completionClaimed,
                interruptExecution
        );
        if (timeoutFinalization == null) {
            return false;
        }

        timeoutFinalization.run();
        return true;
    }

    public Runnable claimTimeout(
            ClaimedJob claimedJob,
            AtomicBoolean completionClaimed,
            Runnable interruptExecution
    ) {
        Objects.requireNonNull(claimedJob, "claimedJob must not be null");
        Objects.requireNonNull(
                completionClaimed,
                "completionClaimed must not be null"
        );
        Objects.requireNonNull(
                interruptExecution,
                "interruptExecution must not be null"
        );

        Instant finishedAt = clock.instant();
        JobExecutionTimeoutException timeout =
                new JobExecutionTimeoutException(
                        claimedJob.job().id(),
                        claimedJob.job().timeoutSeconds()
                );
        ExecutionResult result = ExecutionResult.failed(
                ExecutionStatus.TIMED_OUT,
                finishedAt,
                calculateDurationMs(claimedJob, finishedAt),
                "EXECUTION_TIMEOUT",
                timeout.getMessage()
        );

        if (!completionClaimed.compareAndSet(false, true)) {
            return null;
        }

        interruptExecution.run();

        return () -> {
            try {
                jobExecutionCompletionService.complete(
                        claimedJob,
                        result,
                        timeout
                );
            } catch (RuntimeException exception) {
                completionClaimed.set(false);
                throw exception;
            }
        };
    }

    private void completeSuccessfully(
            ClaimedJob claimedJob,
            AtomicBoolean completionClaimed
    ) {

        Instant finishedAt = clock.instant();

        long durationMs = calculateDurationMs(
                        claimedJob,
                        finishedAt
                );

        ExecutionResult result = ExecutionResult.succeeded(
                        finishedAt,
                        durationMs
                );

        boolean completed = completeOnce(
                completionClaimed,
                () -> jobExecutionCompletionService.complete(
                        claimedJob,
                        result
                )
        );

        if (completed) {
            logger.info(
                """
                Job execution succeeded. \
                jobId={}, executionId={}
                """,
                claimedJob.job().id(),
                claimedJob.execution().id()
            );
        }
    }

    private void completeWithFailure(
            ClaimedJob claimedJob,
            Exception exception,
            AtomicBoolean completionClaimed
    ) {

        Instant finishedAt =
                clock.instant();

        long durationMs =
                calculateDurationMs(
                        claimedJob,
                        finishedAt
                );

        ExecutionResult result =
                ExecutionResult.failed(
                        ExecutionStatus.FAILED,
                        finishedAt,
                        durationMs,
                        exception.getClass()
                                .getSimpleName(),
                        exception.getMessage()
                );

        boolean completed = completeOnce(
                completionClaimed,
                () -> jobExecutionCompletionService.complete(
                        claimedJob,
                        result,
                        exception
                )
        );

        if (completed) {
            logger.warn(
                """
                Job execution failed. \
                jobId={}, executionId={}
                """,
                claimedJob.job().id(),
                claimedJob.execution().id(),
                exception
            );
        }
    }

    private boolean completeOnce(
            AtomicBoolean completionClaimed,
            Runnable completion
    ) {
        if (!completionClaimed.compareAndSet(false, true)) {
            return false;
        }

        try {
            completion.run();
            return true;
        } catch (RuntimeException exception) {
            completionClaimed.set(false);
            throw exception;
        }
    }

    private long calculateDurationMs(
            ClaimedJob claimedJob,
            Instant finishedAt
    ) {

        long durationMs =
                Duration.between(
                        claimedJob.execution().startedAt(),
                        finishedAt).toMillis();

        //n rare cases (like system clock adjustments or sub-millisecond execution times),
        // Duration.between() might yield a tiny negative value. Math.max(0L, durationMs)
        // guarantees duration is never negative, upholding the ExecutionResult invariant rule! 🛡
        return Math.max(0L, durationMs);
    }
}
