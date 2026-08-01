package com.chronosq.scheduler;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "chronosq.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)

//JobScheduler is the automated background poller that periodically
//triggers job promotions in ChronosQ
public class JobScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    JobScheduler.class
            );

    private final ScheduledJobService
            scheduledJobService;

    private final Clock clock;


    @Scheduled(
            fixedDelayString =
                    "${chronosq.scheduler.poll-interval-ms:1000}",
            initialDelayString =
                    "${chronosq.scheduler.poll-interval-ms:1000}"
    )
    public void runSchedulingCycle() {

        Instant currentTime =
                clock.instant();

        try {
            int promotedJobs =
                    scheduledJobService
                            .promoteDueJobs(
                                    currentTime
                            );

            if (promotedJobs > 0) {
                logger.info(
                        "Promoted {} scheduled jobs to READY",
                        promotedJobs
                );
            }
        } catch (Exception exception) {

            logger.error(
                    "Scheduled job promotion failed",
                    exception
            );
        }
    }
}