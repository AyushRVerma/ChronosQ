package com.chronosq.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import org.springframework.stereotype.Component;

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

        Objects.requireNonNull(
                scheduleType, "scheduleType must not be null"
        );

        Objects.requireNonNull(
                currentTime, "currentTime must not be null"
        );

        Instant availableAt = calculateInitialAvailableAt(
                        scheduleType,
                        requestedAvailableAt,
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
            Instant previousAvailableAt,
            long intervalSeconds,
            Instant currentTime
    ) {

        Objects.requireNonNull(
                previousAvailableAt,
                "previousAvailableAt must not be null"
        );

        Objects.requireNonNull(
                currentTime,
                "currentTime must not be null"
        );

        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException(
                    """
                    intervalSeconds must be \
                    greater than zero
                    """
            );
        }

        Instant nextScheduledTime = previousAvailableAt.plusSeconds(intervalSeconds);

        if (nextScheduledTime.isAfter(currentTime)) {
            return nextScheduledTime;
        }

        //The job would run 120 times in a crazy rapid-fire loop to "catch up" on all missed runs

        //1. Calculate how many seconds have elapsed since last run
       // 7200 seconds
        long elapsedSeconds =
                Duration.between(
                        previousAvailableAt,
                        currentTime
                ).getSeconds();

        //Divide by interval to find how many full intervals were missed
        //7200 / 60 = 120 missed intervals
        long completedIntervals =
                elapsedSeconds / intervalSeconds;


        //Skip all missed intervals and target the VERY NEXT future slot!
        //121
        long intervalsToNextExecution =
                completedIntervals + 1;


        long secondsToNextExecution =
                Math.multiplyExact(
                        intervalsToNextExecution,
                        intervalSeconds
                );

        // correct timing
        return previousAvailableAt.plusSeconds(
                secondsToNextExecution
        );
    }

    //This method determines when the job should be eligible to run
    private Instant calculateInitialAvailableAt(
            ScheduleType scheduleType,
            Instant requestedAvailableAt,
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