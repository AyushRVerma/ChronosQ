package com.chronosq.execution;

import java.time.Instant;
import java.util.Objects;


//ExecutionResult is an immutable value object / record that represents
// the final outcome of running a job execution attempt.
//When a worker thread finishes executing a job handler, it creates an ExecutionResult to
// capture the final status (SUCCEEDED, FAILED, TIMED_OUT, etc.), how long it took in milliseconds (durationMs), and any error traces if it failed.

public record ExecutionResult(

        ExecutionStatus status,

        Instant finishedAt,

        long durationMs,

        String errorType,

        String errorMessage

) {

    public ExecutionResult {

        Objects.requireNonNull(
                status,
                "status must not be null"
        );

        Objects.requireNonNull(
                finishedAt,
                "finishedAt must not be null"
        );

        if (status == ExecutionStatus.RUNNING) {
            throw new IllegalArgumentException(
                    """
                    final execution status cannot \
                    be RUNNING
                    """
            );
        }

        if (durationMs < 0) {
            throw new IllegalArgumentException(
                    "durationMs must not be negative"
            );
        }

        if (status == ExecutionStatus.SUCCEEDED
                && (errorType != null
                || errorMessage != null)) {

            throw new IllegalArgumentException(
                    """
                    successful execution cannot \
                    contain error details
                    """
            );
        }
    }

    //Static Factory Methods.
    public static ExecutionResult succeeded(
            Instant finishedAt,
            long durationMs
    ) {

        return new ExecutionResult(
                ExecutionStatus.SUCCEEDED,
                finishedAt,
                durationMs,
                null,
                null
        );
    }

    //Static Factory Methods.
    public static ExecutionResult failed(
            ExecutionStatus status,
            Instant finishedAt,
            long durationMs,
            String errorType,
            String errorMessage
    ) {

        return new ExecutionResult(
                status,
                finishedAt,
                durationMs,
                errorType,
                errorMessage
        );
    }
}

//ExecutionResult is a validated record that captures the final outcome
// of a job execution attempt. Its constructor validation enforces that a
// finished result must have timing data and cannot hold contradictory data (like errors on success), while its static factory methods (succeeded() / failed()) provide a clean API for worker code.