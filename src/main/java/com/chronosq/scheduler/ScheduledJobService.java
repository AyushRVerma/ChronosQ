package com.chronosq.scheduler;

import java.time.Instant;
import java.util.Objects;

import com.chronosq.configuration
        .SchedulerProperties;

import com.chronosq.job.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation
        .Transactional;

@Service
@RequiredArgsConstructor
// ScheduledJobService is a Spring Service that acts as the coordinator
// for promoting overdue jobs from SCHEDULED ➔ READY.
public class ScheduledJobService {

    private final JobRepository jobRepository;

    private final SchedulerProperties schedulerProperties;

    private final JobScheduleCalculator jobScheduleCalculator;


    @Transactional
    public int promoteDueJobs(
            Instant currentTime
    ) {

        Objects.requireNonNull(
                currentTime,
                "currentTime must not be null"
        );

        if (!schedulerProperties.enabled()) {
            return 0;
        }

        CronHandlingResult cronResult = handleSkippedCronJobs(currentTime);
        int remainingBatchSize =
                schedulerProperties.batchSize() - cronResult.handled();

        int otherPromotedJobs = remainingBatchSize == 0
                ? 0
                : jobRepository.promoteDueJobs(
                        currentTime,
                        remainingBatchSize
                );
        return cronResult.promoted() + otherPromotedJobs;
    }

    private CronHandlingResult handleSkippedCronJobs(Instant currentTime) {
        int promotedCronJobs = 0;
        var skippedCronJobs = jobRepository.findDueSkippedCronJobs(
                currentTime,
                schedulerProperties.batchSize()
        );

        for (var job : skippedCronJobs) {
            Instant followingScheduledTime =
                    jobScheduleCalculator.calculateNextCron(
                            job.availableAt(),
                            job.cronExpression(),
                            job.cronTimeZone()
                    );

            if (followingScheduledTime.isAfter(currentTime)) {
                if (!jobRepository.promoteScheduledJob(
                        job.id(),
                        currentTime,
                        job.version()
                )) {
                    throw new IllegalStateException(
                            "Could not promote due cron job " + job.id()
                    );
                }
                promotedCronJobs++;
                continue;
            }

            Instant nextFutureTime =
                    jobScheduleCalculator.calculateNextCron(
                            currentTime,
                            job.cronExpression(),
                            job.cronTimeZone()
                    );

            if (!jobRepository.rescheduleSkippedCronJob(
                    job.id(),
                    nextFutureTime,
                    currentTime,
                    job.version()
            )) {
                throw new IllegalStateException(
                        "Could not skip missed cron executions for job "
                                + job.id()
                );
            }
        }
        return new CronHandlingResult(
                skippedCronJobs.size(),
                promotedCronJobs
        );
    }

    private record CronHandlingResult(int handled, int promoted) {
    }
}
