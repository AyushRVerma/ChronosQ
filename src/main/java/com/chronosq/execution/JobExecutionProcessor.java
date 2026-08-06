package com.chronosq.execution;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.chronosq.handler.JobHandler;
import com.chronosq.handler.JobHandlerRegistry;
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

    private final Clock clock;


    public void process(ClaimedJob claimedJob) {

        Objects.requireNonNull(
                claimedJob,
                "claimedJob must not be null"
        );

        try {
//            1. Look up the matching JobHandler for this job's type (e.g. "send-email")
            JobHandler handler =
                    jobHandlerRegistry
                            .getRequiredHandler(
                                    claimedJob.job()
                                            .jobType()
                            );
   // 2. Run the actual business logic!
            handler.execute(claimedJob.job());


            // 3. If no exception occurred -> complete successfully!

            completeSuccessfully(claimedJob);

        } catch (Exception exception) {

            completeWithFailure(
                    claimedJob,
                    exception
            );
        }
    }

    private void completeSuccessfully(ClaimedJob claimedJob) {

        Instant finishedAt = clock.instant();

        long durationMs = calculateDurationMs(
                        claimedJob,
                        finishedAt
                );

        ExecutionResult result = ExecutionResult.succeeded(
                        finishedAt,
                        durationMs
                );

        jobExecutionCompletionService.complete(
                claimedJob,
                result
        );

        logger.info(
                """
                Job execution succeeded. \
                jobId={}, executionId={}
                """,
                claimedJob.job().id(),
                claimedJob.execution().id()
        );
    }

    private void completeWithFailure(ClaimedJob claimedJob, Exception exception) {

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

        jobExecutionCompletionService.complete(
                claimedJob,
                result,
                exception
        );

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