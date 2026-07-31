package com.chronosq.api;

import java.time.Instant;

import com.chronosq.job.domain.ScheduleType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;


// This is the DTO used when a client makes an HTTP POST request to submit a new job to ChronosQ.

public record SubmitJobRequest(

        //Jakarta Bean Validation Annotations
        @NotBlank(message = "queueName must not be blank")
        @Size(
                max = 100,
                message = "queueName must not exceed 100 characters"
        )
        String queueName,

        @NotBlank(message = "jobType must not be blank")
        @Size(
                max = 100,
                message = "jobType must not exceed 100 characters"
        )
        String jobType,

        @NotNull(message = "payload must not be null")
        JsonNode payload,

        @Min(
                value = -100,
                message = "priority must be at least -100"
        )
        @Max(
                value = 100,
                message = "priority must not exceed 100"
        )
        Integer priority,

        Instant availableAt,

        @NotNull(message = "scheduleType must not be null")
        ScheduleType scheduleType,

        @Positive(
                message = "intervalSeconds must be greater than zero"
        )
        Long intervalSeconds,

        @Min(
                value = 1,
                message = "maxAttempts must be at least 1"
        )
        @Max(
                value = 100,
                message = "maxAttempts must not exceed 100"
        )
        Integer maxAttempts,

        @Min(
                value = 1,
                message = "timeoutSeconds must be at least 1"
        )
        @Max(
                value = 86_400,
                message = "timeoutSeconds must not exceed 86400"
        )
        Integer timeoutSeconds,

        @Size(
                max = 200,
                message = "idempotencyKey must not exceed 200 characters"
        )
        String idempotencyKey

) {

  // Spring's @Valid processor looks for boolean methods annotated with @AssertTrue. If the method returns false, Spring rejects the HTTP request with a validation error message.
  //jakarta.validation.constraints package) used in Spring Boot to ensure that a specific boolean field or
  // the return value of a method evaluates to true. If the condition is false, Spring's validation framework blocks the request and triggers a validation error
    @AssertTrue(
            message = """
                    ONE_TIME requires availableAt, and \
                    FIXED_INTERVAL requires intervalSeconds
                    """
    )
    public boolean isScheduleConfigurationValid() {

        if (scheduleType == null) {
            return true;
        }

        return switch (scheduleType) {

            case IMMEDIATE ->
                    intervalSeconds == null;

            case ONE_TIME ->
                    availableAt != null
                            && intervalSeconds == null;

            case FIXED_INTERVAL ->
                    intervalSeconds != null
                            && intervalSeconds > 0;
        };
    }

    //Helper Methods
    public int priorityOrDefault() {

        return priority == null
                ? 0
                : priority;
    }

    public int maxAttemptsOrDefault() {

        return maxAttempts == null
                ? 3
                : maxAttempts;
    }

    public int timeoutSecondsOrDefault() {

        return timeoutSeconds == null
                ? 30
                : timeoutSeconds;
    }
}

//SubmitJobRequest validates incoming client HTTP payloads at the door.
// If any constraint fails (e.g. timeoutSeconds is 999999 or queueName is blank),
// Spring automatically returns an HTTP 400 Bad Request before any business logic or database code is touched