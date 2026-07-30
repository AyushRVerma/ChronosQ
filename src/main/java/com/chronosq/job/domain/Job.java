package com.chronosq.job.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Job is the representation of one row in the PostgreSQL jobs table
// A record is a special Java class designed purely to hold data It alr gives you const,getter,equals,hashcode,toString,Immutable

public record Job(
        UUID id,
        String queueName,
        String jobType,
        String payload,
        JobStatus status,
        int priority,
//        // Instant is Java's way of representing a single point in time
        Instant availableAt,
        ScheduleType scheduleType,
        Long intervalSeconds,
        int attemptCount,
        int maxAttempts,
        // if a client submits the same job twice the key ensures only one job is created
        String idempotencyKey,
        String lockedBy,
        Instant leaseExpiresAt,
        int timeoutSeconds,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        //used of Optimistic locking: Prevents two workers from updating the same job simultaneously
        //assumes conflicts are rare
        long version
) {

    //compact constructor: it runs every time a Job object is created
    public Job {
        Objects.requireNonNull(id, "Job ID must not be null");
        Objects.requireNonNull(queueName, "Queue name must not be null");
        Objects.requireNonNull(jobType, "Job type must not be null");
        Objects.requireNonNull(payload, "Payload must not be null");
        Objects.requireNonNull(status, "Job status must not be null");
        Objects.requireNonNull(availableAt, "Available time must not be null");
        Objects.requireNonNull(scheduleType, "Schedule type must not be null");
        Objects.requireNonNull(createdAt, "Created time must not be null");
        Objects.requireNonNull(updatedAt, "Updated time must not be null");

        //Validation rules
        if (queueName.isBlank()) {
            throw new IllegalArgumentException(
                    "Queue name must not be blank"
            );
        }

        if (jobType.isBlank()) {
            throw new IllegalArgumentException(
                    "Job type must not be blank"
            );
        }

        if (attemptCount < 0) {
            throw new IllegalArgumentException(
                    "Attempt count must not be negative"
            );
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be at least 1"
            );
        }

        if (attemptCount > maxAttempts) {
            throw new IllegalArgumentException(
                    "Attempt count must not exceed maximum attempts"
            );
        }

        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException(
                    "Timeout must be at least 1 second"
            );
        }

        if (version < 0) {
            throw new IllegalArgumentException(
                    "Version must not be negative"
            );
        }

        if (scheduleType == ScheduleType.FIXED_INTERVAL) {
            if (intervalSeconds == null || intervalSeconds < 1) {
                throw new IllegalArgumentException(
                        "Fixed-interval jobs require a positive interval"
                );
            }
        }

        if (scheduleType != ScheduleType.FIXED_INTERVAL
                && intervalSeconds != null) {
            throw new IllegalArgumentException(
                    "Only fixed-interval jobs may have an interval"
            );
        }

        if ((lockedBy == null) != (leaseExpiresAt == null)) {
            throw new IllegalArgumentException(
                    "Job owner and lease expiry must both be set or both be null"
            );
        }
    }

    //helper methods
    public boolean isTerminal() {
        return status == JobStatus.SUCCEEDED
                || status == JobStatus.DEAD_LETTERED
                || status == JobStatus.CANCELLED;
    }

    public boolean hasAttemptsRemaining() {
        return attemptCount < maxAttempts;
    }

    public boolean isAvailableAt(Instant time) {
        Objects.requireNonNull(time, "Time must not be null");

        return !availableAt.isAfter(time);
    }
}