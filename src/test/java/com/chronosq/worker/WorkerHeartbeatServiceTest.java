package com.chronosq.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronosq.configuration.HeartbeatProperties;
import com.chronosq.configuration.WorkerProperties;

@ExtendWith(MockitoExtension.class)
class WorkerHeartbeatServiceTest {

    private static final Instant CURRENT_TIME =
            Instant.parse("2026-08-06T10:00:00Z");

    @Mock
    private WorkerNodeRepository workerNodeRepository;

    private WorkerHeartbeatService heartbeatService;

    @BeforeEach
    void setUp() {
        WorkerProperties workerProperties =
                new WorkerProperties(
                        true,
                        "worker-1",
                        "local-instance",
                        "default",
                        10,
                        60
                );

        HeartbeatProperties heartbeatProperties =
                new HeartbeatProperties(
                        true,
                        5_000
                );

        Clock clock = Clock.fixed(
                CURRENT_TIME,
                ZoneOffset.UTC
        );

        heartbeatService = new WorkerHeartbeatService(
                workerNodeRepository,
                workerProperties,
                heartbeatProperties,
                clock
        );
    }

    @Test
    void shouldSendWorkerHeartbeat() {
        heartbeatService.sendHeartbeat();

        verify(workerNodeRepository)
                .registerOrHeartbeat(
                        "worker-1",
                        "local-instance",
                        CURRENT_TIME
                );
    }

    @Test
    void shouldNotCrashWhenHeartbeatFails() {
        doThrow(
                new IllegalStateException(
                        "Database temporarily unavailable"
                )
        )
                .when(workerNodeRepository)
                .registerOrHeartbeat(
                        "worker-1",
                        "local-instance",
                        CURRENT_TIME
                );

        assertThatCode(
                heartbeatService::sendHeartbeat
        ).doesNotThrowAnyException();
    }
}