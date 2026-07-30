package com.chronosq.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

// A worker is a process/thread that picks up jobs from the queue and executes them
// A WorkerNode is the record of that worker who it is, what state it's in, and when it last checked in.
// WorkerNode's main job is enabling failure detection

public record WorkerNode(
        String workerId,
        String instanceName,
        WorkerStatus status,
        Instant lastHeartbeatAt,
        Instant startedAt
) {

    public WorkerNode {
        Objects.requireNonNull(
                workerId,
                "Worker ID must not be null"
        );

        Objects.requireNonNull(
                status,
                "Worker status must not be null"
        );

        Objects.requireNonNull(
                lastHeartbeatAt,
                "Last heartbeat time must not be null"
        );

        Objects.requireNonNull(
                startedAt,
                "Worker start time must not be null"
        );

        if (workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Worker ID must not be blank"
            );
        }

        if (instanceName != null && instanceName.isBlank()) {
            throw new IllegalArgumentException(
                    "Instance name must not be blank when provided"
            );
        }

        if (lastHeartbeatAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Last heartbeat time must not be before worker start time"
            );
        }
    }

    public boolean isActive() {
        return status == WorkerStatus.ACTIVE;
    }

    public boolean hasHeartbeatExpiredAt(
            Instant time,
            Duration maximumHeartbeatAge
    ) {
        Objects.requireNonNull(
                time,
                "Time must not be null"
        );

        Objects.requireNonNull(
                maximumHeartbeatAge,
                "Maximum heartbeat age must not be null"
        );

        if (maximumHeartbeatAge.isZero()
                || maximumHeartbeatAge.isNegative()) {
            throw new IllegalArgumentException(
                    "Maximum heartbeat age must be positive"
            );
        }

        Instant expiryTime =
                lastHeartbeatAt.plus(maximumHeartbeatAge);

        return !expiryTime.isAfter(time);
    }
}