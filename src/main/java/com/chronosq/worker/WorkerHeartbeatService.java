package com.chronosq.worker;

import java.time.Clock;

import com.chronosq.configuration.WorkerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.chronosq.configuration.HeartbeatProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "chronosq.heartbeat",
        name = "enabled",
        havingValue = "true"
)
public class WorkerHeartbeatService {

    private final WorkerNodeRepository workerNodeRepository;
    private final WorkerProperties workerProperties;
    private final HeartbeatProperties heartbeatProperties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString =
                    "${chronosq.heartbeat.interval-ms:5000}",
            initialDelayString =
                    "${chronosq.heartbeat.interval-ms:5000}"
    )
    public void sendHeartbeat() {
        try {
            workerNodeRepository.registerOrHeartbeat(
                    workerProperties.workerId(),
                    workerProperties.instanceName(),
                    clock.instant()
            );
        } catch (Exception exception) {
            log.error(
                    "Failed to send worker heartbeat: workerId={}",
                    workerProperties.workerId(),
                    exception
            );
        }
    }
}