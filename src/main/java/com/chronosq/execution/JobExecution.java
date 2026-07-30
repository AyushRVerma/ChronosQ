package com.chronosq.execution;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// If Job is what needs to be done, then JobExecution is the log of one
// actual attempt to do it
// One Job can have multiple executions

public record JobExecution(
        UUID id,         // Unique ID of THIS execution (not the job)
        UUID jobId,       // Which job is this an execution of?
        String workerId,    // Which worker ran it? e.g. "worker-node-3"
        int attemptNumber,  // 1st try? 2nd try? 3rd try?
        ExecutionStatus status,    // RUNNING, SUCCEEDED, FAILED, TIMED_OUT, ABANDONED
        Instant startedAt,         // When worker started executing
        Instant finishedAt,        // When it finished (null if still running)
        Long durationMs,           // How long it took in milliseconds (null if running)
        String errorType,          // e.g. "NullPointerException" (null if succeeded)
        String errorMessage       // e.g. "Connection refused" (null if succeeded)
) {

    public JobExecution {
        Objects.requireNonNull(
                id,
                "Execution ID must not be null"
        );

        Objects.requireNonNull(
                jobId,
                "Job ID must not be null"
        );

        Objects.requireNonNull(
                workerId,
                "Worker ID must not be null"
        );

        Objects.requireNonNull(
                status,
                "Execution status must not be null"
        );

        Objects.requireNonNull(
                startedAt,
                "Execution start time must not be null"
        );

        if (workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Worker ID must not be blank"
            );
        }

        if (attemptNumber < 1) {
            throw new IllegalArgumentException(
                    "Attempt number must be at least 1"
            );
        }

        if (durationMs != null && durationMs < 0) {
            throw new IllegalArgumentException(
                    "Execution duration must not be negative"
            );
        }

        if (finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Execution finish time must not be before start time"
            );
        }

        if (status == ExecutionStatus.RUNNING) {
            if (finishedAt != null || durationMs != null) {
                throw new IllegalArgumentException(
                        "Running execution must not have finish information"
                );
            }

            if (errorType != null || errorMessage != null) {
                throw new IllegalArgumentException(
                        "Running execution must not have error information"
                );
            }
        }

        if (status != ExecutionStatus.RUNNING) {
            if (finishedAt == null || durationMs == null) {
                throw new IllegalArgumentException(
                        "Finished execution requires finish time and duration"
                );
            }
        }

        if (status == ExecutionStatus.SUCCEEDED) {
            if (errorType != null || errorMessage != null) {
                throw new IllegalArgumentException(
                        "Successful execution must not have error information"
                );
            }
        }
    }

    public boolean isFinished() {
        return status != ExecutionStatus.RUNNING;
    }

    public boolean wasSuccessful() {
        return status == ExecutionStatus.SUCCEEDED;
    }

    public boolean failed() {
        return status == ExecutionStatus.FAILED
                || status == ExecutionStatus.TIMED_OUT
                || status == ExecutionStatus.ABANDONED;
    }
}

// Job submitted
//     │
//     ▼
// Job status → RUNNING
// JobExecution created → { status: RUNNING, startedAt: now, finishedAt: null }
//     │
//     ├── Success?
//     │     ▼
//     │   JobExecution updated → { status: SUCCEEDED, finishedAt: now, durationMs: 1200 }
//     │   Job status → SUCCEEDED
//     │
//     └── Failed?
//           ▼
//         JobExecution updated → { status: FAILED, errorType: "IOException", durationMs: 500 }
//         Job.hasAttemptsRemaining()?
//           ├── YES → Job status → RETRY_WAIT (new JobExecution on next try)
//           └── NO  → Job status → DEAD_LETTERED