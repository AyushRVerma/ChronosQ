package com.chronosq.api;

import java.time.Instant;
import java.util.UUID;

import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import tools.jackson.databind.JsonNode;

//Data Transfer Object
//t represents the JSON response that will be sent back
// to external clients (like a React frontend or another microservice)
// over HTTP when they query a job.

public record JobResponse(

        UUID id,

        String queueName,

        String jobType,

        JsonNode payload,  // Parsed JSON payload object

        JobStatus status,

        int priority,

        Instant availableAt,

        ScheduleType scheduleType,

        Long intervalSeconds,

        int attemptCount,

        int maxAttempts,

        String idempotencyKey,

        int timeoutSeconds,

        Instant createdAt,

        Instant updatedAt,

        Instant completedAt

) {
}

