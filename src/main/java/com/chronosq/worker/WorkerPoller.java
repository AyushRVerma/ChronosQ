package com.chronosq.worker;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.chronosq.execution.JobExecutionDispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "chronosq.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)

//JobScheduler (which polled PostgreSQL to promote SCHEDULED ➔ READY jobs). Now,
// WorkerPoller is the background poller that pulls READY jobs from PostgreSQL and dispatches
// them to worker threads!
public class WorkerPoller {


    private final JobClaimService jobClaimService;

    private final JobExecutionDispatcher jobExecutionDispatcher;

    private final Clock clock;


    @Scheduled(
            fixedDelayString =
                    "${chronosq.execution.poll-interval-ms:1000}",
            initialDelayString =
                    "${chronosq.execution.poll-interval-ms:1000}"
    )
    public void pollAndDispatch() {

        try {
            Instant currentTime = clock.instant();

            List<ClaimedJob> claimedJobs = jobClaimService
                            .claimAvailableJobs(
                                    currentTime
                            );

            int dispatchedCount = jobExecutionDispatcher.dispatch(
                            claimedJobs
                    );

            if (dispatchedCount > 0) {
                log.info(
                        """
                        Claimed and dispatched {} jobs \
                        for execution
                        """,
                        dispatchedCount
                );
            }

        } catch (Exception exception) {
            log.error(
                    "Worker polling cycle failed",
                    exception
            );
        }
    }
}

//JobScheduler (which polled PostgreSQL to promote SCHEDULED ➔ READY jobs). Now,
// WorkerPoller is the background poller that pulls READY jobs from PostgreSQL and dispatches them
// to worker threads!