package com.chronosq.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Clock;
import java.util.UUID;
import com.chronosq.security.TestJwtKeyConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(TestJwtKeyConfiguration.class)
class DashboardQueryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private DashboardQueryService dashboardQueryService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM job_executions").update();
        jdbcClient.sql("DELETE FROM jobs").update();
        jdbcClient.sql("DELETE FROM worker_nodes").update();
    }

    @Test
    void shouldReturnOperationalSnapshotFromDatabase() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID succeededJobId = UUID.randomUUID();
        UUID deadLetterJobId = UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO worker_nodes (
                    worker_id,
                    instance_name,
                    status,
                    last_heartbeat_at,
                    started_at
                )
                VALUES (
                    'worker-test',
                    'integration-test',
                    'ACTIVE',
                    :now,
                    :startedAt
                )
                """)
                .param("now", now)
                .param("startedAt", now.minusMinutes(5))
                .update();

        insertJob(
                succeededJobId,
                "SUCCEEDED",
                2,
                now.minusSeconds(5),
                now
        );
        insertJob(
                deadLetterJobId,
                "DEAD_LETTERED",
                3,
                now.minusSeconds(10),
                now
        );

        jdbcClient.sql("""
                INSERT INTO job_executions (
                    id,
                    job_id,
                    worker_id,
                    attempt_number,
                    status,
                    started_at,
                    finished_at,
                    duration_ms
                )
                VALUES (
                    :id,
                    :jobId,
                    'worker-test',
                    2,
                    'SUCCEEDED',
                    :startedAt,
                    :finishedAt,
                    1000
                )
                """)
                .param("id", UUID.randomUUID())
                .param("jobId", succeededJobId)
                .param("startedAt", now.minusSeconds(4))
                .param("finishedAt", now.minusSeconds(3))
                .update();

        DashboardResponse response =
                dashboardQueryService.loadDashboard();

        assertThat(dashboardQueryService.loadDashboard()).isSameAs(response);

        assertThat(response.summary().completedLastHour()).isEqualTo(1);
        assertThat(response.summary().deadLetter()).isEqualTo(1);
        assertThat(response.summary().retryAttemptsLastHour()).isEqualTo(1);
        assertThat(response.workers())
                .singleElement()
                .satisfies(worker -> {
                    assertThat(worker.workerId()).isEqualTo("worker-test");
                    assertThat(worker.activeLeases()).isZero();
                });
        assertThat(response.recentJobs()).hasSize(2);
        assertThat(response.performance()).hasSize(10);
        assertThat(response.performance())
                .extracting(DashboardResponse.PerformancePoint::completed)
                .contains(1L);
    }

    @Test
    void shouldInstallQueueAndDashboardIndexes() {
        var indexNames = jdbcClient.sql("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename IN ('jobs', 'job_executions')
                """)
                .query(String.class)
                .list();

        assertThat(indexNames).contains(
                "idx_jobs_queue_claim",
                "idx_jobs_dashboard_recent",
                "idx_jobs_dashboard_completed",
                "idx_jobs_running_worker",
                "idx_job_executions_dashboard_started"
        );
    }

    @Test
    void retryRescheduleDoesNotChangeRecordedAttemptLatency() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID jobId = UUID.randomUUID();
        insertJob(jobId, "RETRY_WAIT", 1, now.minusSeconds(10), now);

        jdbcClient.sql("""
                INSERT INTO job_executions (
                    id, job_id, worker_id, attempt_number, status,
                    started_at, scheduled_available_at, finished_at, duration_ms
                ) VALUES (
                    :id, :jobId, 'worker-test', 1, 'FAILED',
                    :startedAt, :scheduledAt, :finishedAt, 1000
                )
                """)
                .param("id", UUID.randomUUID())
                .param("jobId", jobId)
                .param("startedAt", now.minusSeconds(8))
                .param("scheduledAt", now.minusSeconds(10))
                .param("finishedAt", now.minusSeconds(7))
                .update();

        jdbcClient.sql("UPDATE jobs SET available_at = :retryAt WHERE id = :jobId")
                .param("retryAt", now.plusMinutes(5))
                .param("jobId", jobId)
                .update();

        DashboardResponse response = new DashboardQueryService(jdbcClient, Clock.systemUTC())
                .loadDashboard();
        assertThat(response.performance())
                .extracting(DashboardResponse.PerformancePoint::p95StartLatencyMs)
                .contains(2000L);
    }

    private void insertJob(
            UUID id,
            String status,
            int attempts,
            OffsetDateTime availableAt,
            OffsetDateTime now
    ) {
        jdbcClient.sql("""
                INSERT INTO jobs (
                    id,
                    queue_name,
                    job_type,
                    payload,
                    status,
                    priority,
                    available_at,
                    schedule_type,
                    attempt_count,
                    max_attempts,
                    timeout_seconds,
                    created_at,
                    updated_at,
                    completed_at
                )
                VALUES (
                    :id,
                    'default',
                    'PRINT_MESSAGE',
                    '{"message":"dashboard test"}',
                    :status,
                    0,
                    :availableAt,
                    'IMMEDIATE',
                    :attempts,
                    3,
                    30,
                    :createdAt,
                    :updatedAt,
                    :completedAt
                )
                """)
                .param("id", id)
                .param("status", status)
                .param("availableAt", availableAt)
                .param("attempts", attempts)
                .param("createdAt", now.minusMinutes(1))
                .param("updatedAt", now)
                .param("completedAt", now)
                .update();
    }
}
