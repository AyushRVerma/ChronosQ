package com.chronosq.api;

import java.time.Instant;
import java.util.UUID;

import com.chronosq.execution.ExecutionStatus;

public record JobExecutionResponse(

        UUID id,

        UUID jobId,

        String workerId,

        int attemptNumber,

        ExecutionStatus status,

        Instant startedAt,

        Instant finishedAt,

        Long durationMs,

        String errorType,

        String errorMessage

) {
}