package com.chronosq.recovery;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "chronosq.recovery",
        name = "enabled",
        havingValue = "true"
)
public class ExpiredLeaseRecoveryScheduler {

    private final ExpiredLeaseRecoveryService recoveryService;
    private final Clock clock;

    @Scheduled(
            fixedDelayString =
                    "${chronosq.recovery.poll-interval-ms:10000}",
            initialDelayString =
                    "${chronosq.recovery.poll-interval-ms:10000}"
    )
    public void recoverExpiredJobs() {
        try {
            RecoveryResult result =
                    recoveryService.recoverExpiredJobs(
                            clock.instant()
                    );

            if (result.totalRecoveredJobCount() > 0) {
                log.warn(
                        "Recovered expired jobs: retried={}, "
                                + "deadLettered={}",
                        result.retriedJobCount(),
                        result.deadLetteredJobCount()
                );
            }
        } catch (Exception exception) {
            log.error(
                    "Failed to recover expired jobs",
                    exception
            );
        }
    }
}