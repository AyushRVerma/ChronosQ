package com.chronosq.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class WorkerNodeTest {

    private static final Instant STARTED_AT =
            Instant.parse("2026-07-30T10:00:00Z");

    private static final Instant HEARTBEAT_AT =
            Instant.parse("2026-07-30T10:00:10Z");

    @Test
    void shouldCreateActiveWorker() {
        WorkerNode worker = new WorkerNode(
                "worker-a",
                "chronosq-instance-1",
                WorkerStatus.ACTIVE,
                HEARTBEAT_AT,
                STARTED_AT
        );

        assertThat(worker.workerId()).isEqualTo("worker-a");

        assertThat(worker.instanceName())
                .isEqualTo("chronosq-instance-1");

        assertThat(worker.status())
                .isEqualTo(WorkerStatus.ACTIVE);

        assertThat(worker.isActive()).isTrue();
    }

    @Test
    void shouldRecognizeStoppedWorker() {
        WorkerNode worker = new WorkerNode(
                "worker-a",
                "chronosq-instance-1",
                WorkerStatus.STOPPED,
                HEARTBEAT_AT,
                STARTED_AT
        );

        assertThat(worker.isActive()).isFalse();
    }

    @Test
    void shouldReportHeartbeatAsNotExpired() {
        WorkerNode worker = createActiveWorker();

        boolean expired = worker.hasHeartbeatExpiredAt(
                HEARTBEAT_AT.plusSeconds(20),
                Duration.ofSeconds(30)
        );

        assertThat(expired).isFalse();
    }

    @Test
    void shouldReportHeartbeatAsExpired() {
        WorkerNode worker = createActiveWorker();

        boolean expired = worker.hasHeartbeatExpiredAt(
                HEARTBEAT_AT.plusSeconds(31),
                Duration.ofSeconds(30)
        );

        assertThat(expired).isTrue();
    }

    @Test
    void shouldTreatExactExpiryTimeAsExpired() {
        WorkerNode worker = createActiveWorker();

        boolean expired = worker.hasHeartbeatExpiredAt(
                HEARTBEAT_AT.plusSeconds(30),
                Duration.ofSeconds(30)
        );

        assertThat(expired).isTrue();
    }

    @Test
    void shouldRejectNullWorkerId() {
        assertThatThrownBy(
                () -> new WorkerNode(
                        null,
                        "chronosq-instance-1",
                        WorkerStatus.ACTIVE,
                        HEARTBEAT_AT,
                        STARTED_AT
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Worker ID must not be null");
    }

    @Test
    void shouldRejectBlankWorkerId() {
        assertThatThrownBy(
                () -> new WorkerNode(
                        "   ",
                        "chronosq-instance-1",
                        WorkerStatus.ACTIVE,
                        HEARTBEAT_AT,
                        STARTED_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Worker ID must not be blank");
    }

    @Test
    void shouldRejectBlankInstanceName() {
        assertThatThrownBy(
                () -> new WorkerNode(
                        "worker-a",
                        "   ",
                        WorkerStatus.ACTIVE,
                        HEARTBEAT_AT,
                        STARTED_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Instance name must not be blank when provided"
                );
    }

    @Test
    void shouldRejectHeartbeatBeforeStartTime() {
        assertThatThrownBy(
                () -> new WorkerNode(
                        "worker-a",
                        "chronosq-instance-1",
                        WorkerStatus.ACTIVE,
                        STARTED_AT.minusSeconds(1),
                        STARTED_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Last heartbeat time must not be before worker start time"
                );
    }

    @Test
    void shouldRejectZeroMaximumHeartbeatAge() {
        WorkerNode worker = createActiveWorker();

        assertThatThrownBy(
                () -> worker.hasHeartbeatExpiredAt(
                        HEARTBEAT_AT.plusSeconds(10),
                        Duration.ZERO
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Maximum heartbeat age must be positive"
                );
    }

    @Test
    void shouldRejectNegativeMaximumHeartbeatAge() {
        WorkerNode worker = createActiveWorker();

        assertThatThrownBy(
                () -> worker.hasHeartbeatExpiredAt(
                        HEARTBEAT_AT.plusSeconds(10),
                        Duration.ofSeconds(-1)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Maximum heartbeat age must be positive"
                );
    }

    private static WorkerNode createActiveWorker() {
        return new WorkerNode(
                "worker-a",
                "chronosq-instance-1",
                WorkerStatus.ACTIVE,
                HEARTBEAT_AT,
                STARTED_AT
        );
    }
}