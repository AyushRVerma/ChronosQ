package com.chronosq.worker;

import java.time.Instant;
import java.util.Optional;


// WorkerNodeRepository defines the contract for managing worker node
// registration, heartbeat updates, and worker status tracking in PostgreSQL.
public interface WorkerNodeRepository {

    // Registers a new worker node when it boots up for the first time.
    void save(WorkerNode workerNode);

    //  Looks up a worker node by its unique ID.
    Optional<WorkerNode> findByWorkerId(
            String workerId
    );

    // Periodically updates the last_heartbeat_at timestamp of an active worker.
    boolean updateHeartbeat(
            String workerId,
            Instant heartbeatTime
    );

    // Updates worker state (ACTIVE, IDLE, DRAINING, STOPPED).
    boolean updateStatus(
            String workerId,
            WorkerStatus status
    );
}