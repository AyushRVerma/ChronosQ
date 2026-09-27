package com.chronosq.api;

import java.time.Instant;
import java.time.ZoneId;

import com.chronosq.job.domain.MissedExecutionPolicy;
import com.chronosq.job.domain.ScheduleType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;
import org.springframework.scheduling.support.CronExpression;


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

        @Size(max = 200, message = "cronExpression must not exceed 200 characters")
        String cronExpression,

        @Size(max = 100, message = "cronTimeZone must not exceed 100 characters")
        String cronTimeZone,

        MissedExecutionPolicy missedExecutionPolicy,

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

    public SubmitJobRequest {
        // Jackson represents an explicit JSON null as NullNode, which
        // @NotNull would otherwise accept as a non-null Java object.
        if (payload != null && payload.isNull()) {
            payload = null;
        }
    }

  // Spring's @Valid processor looks for boolean methods annotated with @AssertTrue. If the method returns false, Spring rejects the HTTP request with a validation error message.
  //jakarta.validation.constraints package) used in Spring Boot to ensure that a specific boolean field or
  // the return value of a method evaluates to true. If the condition is false, Spring's validation framework blocks the request and triggers a validation error
    @AssertTrue(
            message = """
                    ONE_TIME requires availableAt, and \
                    FIXED_INTERVAL requires intervalSeconds, and CRON requires a valid expression, time zone and missed-execution policy
                    """
    )
    public boolean isScheduleConfigurationValid() {

        if (scheduleType == null) {
            return true;
        }

        return switch (scheduleType) {

            case IMMEDIATE ->
                    intervalSeconds == null && noCronConfiguration();

            case ONE_TIME ->
                    availableAt != null
                            && intervalSeconds == null
                            && noCronConfiguration();

            case FIXED_INTERVAL ->
                    intervalSeconds != null
                            && intervalSeconds > 0
                            && noCronConfiguration();

            case CRON -> isValidCronConfiguration();
        };
    }

    private boolean noCronConfiguration() {
        return cronExpression == null
                && cronTimeZone == null
                && missedExecutionPolicy == null;
    }

    private boolean isValidCronConfiguration() {
        if (availableAt != null || intervalSeconds != null
                || cronExpression == null || cronExpression.isBlank()
                || cronTimeZone == null || cronTimeZone.isBlank()
                || missedExecutionPolicy == null
                || !CronExpression.isValidExpression(cronExpression)) {
            return false;
        }
        try {
            ZoneId.of(cronTimeZone);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public SubmitJobRequest(
            String queueName,
            String jobType,
            JsonNode payload,
            Integer priority,
            Instant availableAt,
            ScheduleType scheduleType,
            Long intervalSeconds,
            Integer maxAttempts,
            Integer timeoutSeconds,
            String idempotencyKey
    ) {
        this(
                queueName, jobType, payload, priority, availableAt,
                scheduleType, intervalSeconds, null, null, null,
                maxAttempts, timeoutSeconds, idempotencyKey
        );
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
