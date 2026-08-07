package com.chronosq.metrics;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.chronosq.execution.ExecutionResult;
import com.chronosq.worker.ClaimedJob;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobExecutionObserver {

    private final ChronosQMetrics chronosQMetrics;
    private final AfterCommitExecutor afterCommitExecutor;

    public JobLogContext openLogContext(
            ClaimedJob claimedJob
    ) {
        Objects.requireNonNull(
                claimedJob,
                "claimedJob must not be null"
        );

        return JobLogContext.open(
                claimedJob
        );
    }

    public void recordCompletedExecution(
            ExecutionResult executionResult
    ) {
        Objects.requireNonNull(
                executionResult,
                "executionResult must not be null"
        );

        afterCommitExecutor.execute(
                () -> chronosQMetrics.recordExecution(
                        executionResult.status(),
                        executionResult.durationMs()
                )
        );
    }
}

//It tags your server logs (Logging Setup): When openLogContext is called, it temporarily attaches
// the jobId to your server's memory. This ensures every single log.info() or log.error() printed during that job includes
// the job's ID, making it easy to search your logs later.
//
//It updates your dashboards safely (Metrics): When a job finishes, recordCompletedExecution sends
// the success/failure stats and the execution duration to your monitoring dashboard (like Grafana).
//
//It prevents "Ghost Metrics": Notice it uses afterCommitExecutor! It deliberately waits to send those stats to your dashboard until after the database has successfully saved the job. If the database crashes, the dashboard is never updated, preventing false data.