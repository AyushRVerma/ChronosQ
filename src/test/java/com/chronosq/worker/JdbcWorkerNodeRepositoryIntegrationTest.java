package com.chronosq.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class JdbcWorkerNodeRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgresContainer =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private WorkerNodeRepository workerNodeRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {

        jdbcClient.sql("""
                DELETE FROM worker_nodes
                """)
                .update();
    }

    @Test
    void shouldSaveAndFindWorkerNode() {

        WorkerNode workerNode = new WorkerNode(
                "worker-1",
                "chronosq-instance-1",
                WorkerStatus.ACTIVE,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T09:59:00Z")
        );

        workerNodeRepository.save(workerNode);

        WorkerNode savedWorker =
                workerNodeRepository
                        .findByWorkerId("worker-1")
                        .orElseThrow();

        assertThat(savedWorker)
                .isEqualTo(workerNode);
    }

    @Test
    void shouldUpdateWorkerHeartbeat() {

        WorkerNode workerNode = createWorker();
        workerNodeRepository.save(workerNode);

        Instant newHeartbeat =
                Instant.parse("2026-01-01T10:05:00Z");

        boolean updated =
                workerNodeRepository.updateHeartbeat(
                        workerNode.workerId(),
                        newHeartbeat
                );

        WorkerNode savedWorker =
                workerNodeRepository
                        .findByWorkerId(workerNode.workerId())
                        .orElseThrow();

        assertThat(updated).isTrue();

        assertThat(savedWorker.lastHeartbeatAt())
                .isEqualTo(newHeartbeat);
    }

    @Test
    void shouldUpdateWorkerStatus() {

        WorkerNode workerNode = createWorker();
        workerNodeRepository.save(workerNode);

        boolean updated =
                workerNodeRepository.updateStatus(
                        workerNode.workerId(),
                        WorkerStatus.STOPPED
                );

        WorkerNode savedWorker =
                workerNodeRepository
                        .findByWorkerId(workerNode.workerId())
                        .orElseThrow();

        assertThat(updated).isTrue();

        assertThat(savedWorker.status())
                .isEqualTo(WorkerStatus.STOPPED);
    }

    @Test
    void shouldReturnFalseWhenUpdatingMissingWorker() {

        boolean updated =
                workerNodeRepository.updateHeartbeat(
                        "missing-worker",
                        Instant.parse(
                                "2026-01-01T10:05:00Z"
                        )
                );

        assertThat(updated).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenWorkerDoesNotExist() {

        assertThat(
                workerNodeRepository.findByWorkerId(
                        "missing-worker"
                )
        ).isEmpty();
    }

    private WorkerNode createWorker() {

        return new WorkerNode(
                "worker-1",
                "chronosq-instance-1",
                WorkerStatus.ACTIVE,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T09:59:00Z")
        );
    }
}