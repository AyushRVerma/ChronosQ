package com.chronosq.scheduler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.support.CronExpression;

@Component

// JobScheduleCalculator is a pure, domain-level calculator. It contains the math and logic for:
//
//Determining a job's initial status (READY vs SCHEDULED) and availability timestamp when a job is first submitted.
//Calculating the NEXT execution time for recurring jobs (FIXED_INTERVAL) — including handling catch-up logic if the system was offline!

public final class JobScheduleCalculator {

    public ScheduleDecision calculateInitialSchedule(
            ScheduleType scheduleType,
            Instant requestedAvailableAt,
            Instant currentTime
    ) {
        return calculateInitialSchedule(
                scheduleType,
                requestedAvailableAt,
                null,
                null,
                currentTime
        );
    }

    public ScheduleDecision calculateInitialSchedule(
            ScheduleType scheduleType,
            Instant requestedAvailableAt,
            String cronExpression,
            String cronTimeZone,
            Instant currentTime
    ) {

        Objects.requireNonNull(
                scheduleType, "scheduleType must not be null"
        );

        Objects.requireNonNull(
                currentTime, "currentTime must not be null"
        );

        Instant availableAt = calculateInitialAvailableAt(
                        scheduleType,
                        requestedAvailableAt,
                        cronExpression,
                        cronTimeZone,
                        currentTime
                );

        JobStatus initialStatus = calculateInitialStatus(
                        scheduleType,
                        availableAt,
                        currentTime
                );

        return new ScheduleDecision(
                availableAt,
                initialStatus
        );
    }

    public Instant calculateNextFixedInterval(
            Instant completedAt,
            long intervalSeconds
    ) {

        Objects.requireNonNull(
                completedAt,
                "completedAt must not be null"
        );

        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException(
                    """
                    intervalSeconds must be \
                    greater than zero
                    """
            );
        }

        return completedAt.plusSeconds(intervalSeconds);
    }

    public Instant calculateNextCron(
            Instant after,
            String expression,
            String timeZone
    ) {
        Objects.requireNonNull(after, "after must not be null");
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(
                    "cron expression must not be blank"
            );
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(timeZone);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "cron time zone is invalid: " + timeZone,
                    exception
            );
        }

        CronExpression cron;
        try {
            cron = CronExpression.parse(expression);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "cron expression is invalid: " + expression,
                    exception
            );
        }

        ZonedDateTime next = cron.next(after.atZone(zone));
        if (next == null) {
            throw new IllegalArgumentException(
                    "cron expression has no future execution time"
            );
        }
        return next.toInstant();
    }

    //This method determines when the job should be eligible to run
    private Instant calculateInitialAvailableAt(
            ScheduleType scheduleType,
            Instant requestedAvailableAt,
            String cronExpression,
            String cronTimeZone,
            Instant currentTime
    ) {

        return switch (scheduleType) {

            case IMMEDIATE -> currentTime;

            case ONE_TIME -> {

                if (requestedAvailableAt == null) {
                    throw new IllegalArgumentException(
                            """
                            ONE_TIME jobs require \
                            availableAt
                            """
                    );
                }

                yield requestedAvailableAt;
            }

            case FIXED_INTERVAL -> {

                if (requestedAvailableAt == null) {
                    yield currentTime;
                }

                yield requestedAvailableAt;
            }

            case CRON -> calculateNextCron(
                    currentTime,
                    cronExpression,
                    cronTimeZone
            );
        };
    }

    private JobStatus calculateInitialStatus(
            ScheduleType scheduleType,
            Instant availableAt,
            Instant currentTime
    ) {

        if (scheduleType == ScheduleType.IMMEDIATE) {
            return JobStatus.READY;
        }

        if (availableAt.isAfter(currentTime)) {
            return JobStatus.SCHEDULED;
        }

        return JobStatus.READY;
    }

    public record ScheduleDecision(

            Instant availableAt,

            JobStatus initialStatus

    ) {
    }
}
