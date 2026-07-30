package com.chronosq.worker;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkerNodeRepository
        implements WorkerNodeRepository {

    private static final String INSERT_WORKER = """
            INSERT INTO worker_nodes (
                worker_id,
                instance_name,
                status,
                last_heartbeat_at,
                started_at
            )
            VALUES (
                :workerId,
                :instanceName,
                :status,
                :lastHeartbeatAt,
                :startedAt
            )
            ON CONFLICT (worker_id)
            DO UPDATE SET
                instance_name = EXCLUDED.instance_name,
                status = EXCLUDED.status,
                last_heartbeat_at = EXCLUDED.last_heartbeat_at
            """;

    private static final String FIND_BY_WORKER_ID = """
            SELECT
                worker_id,
                instance_name,
                status,
                last_heartbeat_at,
                started_at
            FROM worker_nodes
            WHERE worker_id = :workerId
            """;

    private static final String UPDATE_HEARTBEAT = """
            UPDATE worker_nodes
            SET last_heartbeat_at = :heartbeatTime
            WHERE worker_id = :workerId
            """;

    private static final String UPDATE_STATUS = """
            UPDATE worker_nodes
            SET status = :status
            WHERE worker_id = :workerId
            """;

    //fluent API for running SQL queries in Java.
    //It sits right between raw JDBC and ORM frameworks (like Hibernate/JPA):
    //Combines named parameters (:paramName),
    // fluent method chaining, and automatic mapping into a single, elegant interface:
    private final JdbcClient jdbcClient;
    private final WorkerNodeRowMapper rowMapper;

    public JdbcWorkerNodeRepository(
            JdbcClient jdbcClient,
            WorkerNodeRowMapper rowMapper
    ) {


        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public void save(WorkerNode workerNode) {

        jdbcClient.sql(INSERT_WORKER)
                .param(
                        "workerId",
                        workerNode.workerId()
                )
                .param(
                        "instanceName",
                        workerNode.instanceName()
                )
                .param(
                        "status",
                        workerNode.status().name()
                )
                .param(
                        "lastHeartbeatAt",
                        toOffsetDateTime(
                                workerNode.lastHeartbeatAt()
                        )
                )
                .param(
                        "startedAt",
                        toOffsetDateTime(
                                workerNode.startedAt()
                        )
                )
                .update();
    }

    @Override
    public Optional<WorkerNode> findByWorkerId(
            String workerId
    ) {

        return jdbcClient.sql(FIND_BY_WORKER_ID)
                .param("workerId", workerId)
                .query(rowMapper)
                .optional();
    }

    @Override
    public boolean updateHeartbeat(
            String workerId,
            Instant heartbeatTime
    ) {

        int updatedRows = jdbcClient.sql(UPDATE_HEARTBEAT)
                .param("workerId", workerId)
                .param(
                        "heartbeatTime",
                        toOffsetDateTime(heartbeatTime)
                )
                .update();

        return updatedRows == 1;
    }

    @Override
    public boolean updateStatus(
            String workerId,
            WorkerStatus status
    ) {

        int updatedRows = jdbcClient.sql(UPDATE_STATUS)
                .param("workerId", workerId)
                .param("status", status.name())
                .update();

        return updatedRows == 1;
    }

    private OffsetDateTime toOffsetDateTime(
            Instant instant
    ) {

        if (instant == null) {
            return null;
        }

        return instant.atOffset(ZoneOffset.UTC);
    }
}