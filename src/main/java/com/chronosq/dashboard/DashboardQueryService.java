package com.chronosq.dashboard;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DashboardQueryService {

    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(4);

    private static final String SUMMARY_SQL = """
            SELECT
                (SELECT COUNT(*) FROM jobs
                 WHERE status IN ('SCHEDULED', 'READY')) AS queued,
                (SELECT COUNT(*) FROM jobs
                 WHERE status = 'RUNNING') AS running,
                (SELECT COUNT(*) FROM jobs
                 WHERE status = 'SUCCEEDED'
                   AND completed_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour')
                   AS completed_last_hour,
                (SELECT COUNT(*) FROM jobs
                 WHERE status = 'RETRY_WAIT') AS retrying,
                (SELECT COUNT(*) FROM job_executions
                 WHERE attempt_number > 1
                   AND started_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour')
                   AS retry_attempts_last_hour,
                (SELECT COUNT(*) FROM jobs
                 WHERE status = 'DEAD_LETTERED') AS dead_letter
            """;

    private static final String WORKERS_SQL = """
            SELECT
                worker.worker_id,
                worker.instance_name,
                worker.status,
                worker.last_heartbeat_at,
                worker.started_at,
                COUNT(job.id) FILTER (
                    WHERE job.status = 'RUNNING'
                ) AS active_leases
            FROM worker_nodes worker
            LEFT JOIN jobs job
              ON job.locked_by = worker.worker_id
            GROUP BY
                worker.worker_id,
                worker.instance_name,
                worker.status,
                worker.last_heartbeat_at,
                worker.started_at
            ORDER BY worker.last_heartbeat_at DESC
            """;

    private static final String RECENT_JOBS_SQL = """
            SELECT
                job.id AS job_id,
                job.job_type,
                job.queue_name,
                job.status AS job_status,
                job.attempt_count,
                job.max_attempts,
                job.available_at,
                job.locked_by,
                job.lease_expires_at,
                execution.id AS execution_id,
                execution.status AS execution_status,
                execution.worker_id,
                execution.attempt_number,
                execution.started_at,
                execution.finished_at,
                execution.duration_ms,
                execution.error_type,
                execution.error_message
            FROM jobs job
            LEFT JOIN LATERAL (
                SELECT candidate.*
                FROM job_executions candidate
                WHERE candidate.job_id = job.id
                ORDER BY candidate.attempt_number DESC
                LIMIT 1
            ) execution ON TRUE
            ORDER BY job.updated_at DESC, job.id
            LIMIT :limit
            """;

    private static final String PERFORMANCE_SQL = """
            WITH buckets AS (
                SELECT generate_series(
                    date_trunc('hour', CURRENT_TIMESTAMP) - INTERVAL '9 hours',
                    date_trunc('hour', CURRENT_TIMESTAMP),
                    INTERVAL '1 hour'
                ) AS bucket
            ),
            execution_stats AS (
                SELECT
                    date_trunc('hour', execution.started_at) AS bucket,
                    COUNT(*) FILTER (
                        WHERE execution.status = 'SUCCEEDED'
                    ) AS completed,
                    percentile_cont(0.95) WITHIN GROUP (
                        ORDER BY GREATEST(
                            0,
                            EXTRACT(
                                EPOCH FROM (
                                    execution.started_at - execution.scheduled_available_at
                                )
                            ) * 1000
                        )
                    ) AS p95_start_latency_ms
                FROM job_executions execution
                WHERE execution.started_at
                      >= date_trunc('hour', CURRENT_TIMESTAMP)
                         - INTERVAL '9 hours'
                GROUP BY date_trunc('hour', execution.started_at)
            )
            SELECT
                buckets.bucket,
                COALESCE(execution_stats.completed, 0) AS completed,
                ROUND(
                    COALESCE(
                        execution_stats.p95_start_latency_ms,
                        0
                    )
                )::BIGINT AS p95_start_latency_ms
            FROM buckets
            LEFT JOIN execution_stats
              ON execution_stats.bucket = buckets.bucket
            ORDER BY buckets.bucket
            """;

    private final JdbcClient jdbcClient;
    private final Clock clock;
    private volatile CachedSnapshot cachedSnapshot;

    public DashboardQueryService(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public DashboardResponse loadDashboard() {
        Instant now = clock.instant();
        CachedSnapshot current = cachedSnapshot;
        if (current != null && now.isBefore(current.expiresAt())) {
            return current.response();
        }

        // One request refreshes the snapshot while concurrent viewers reuse it.
        synchronized (this) {
            now = clock.instant();
            current = cachedSnapshot;
            if (current != null && now.isBefore(current.expiresAt())) {
                return current.response();
            }
            DashboardResponse response = queryDashboard(now);
            cachedSnapshot = new CachedSnapshot(response, now.plus(SNAPSHOT_TTL));
            return response;
        }
    }

    private DashboardResponse queryDashboard(Instant generatedAt) {
        DashboardResponse.Summary summary = jdbcClient.sql(SUMMARY_SQL)
                .query((resultSet, rowNumber) -> new DashboardResponse.Summary(
                        resultSet.getLong("queued"),
                        resultSet.getLong("running"),
                        resultSet.getLong("completed_last_hour"),
                        resultSet.getLong("retrying"),
                        resultSet.getLong("retry_attempts_last_hour"),
                        resultSet.getLong("dead_letter")
                ))
                .single();

        List<DashboardResponse.Worker> workers = jdbcClient.sql(WORKERS_SQL)
                .query((resultSet, rowNumber) -> new DashboardResponse.Worker(
                        resultSet.getString("worker_id"),
                        resultSet.getString("instance_name"),
                        resultSet.getString("status"),
                        toInstant(resultSet, "last_heartbeat_at"),
                        toInstant(resultSet, "started_at"),
                        resultSet.getLong("active_leases")
                ))
                .list();

        List<DashboardResponse.RecentJob> recentJobs =
                jdbcClient.sql(RECENT_JOBS_SQL)
                        .param("limit", 50)
                        .query((resultSet, rowNumber) -> new DashboardResponse.RecentJob(
                                resultSet.getObject("job_id", UUID.class),
                                resultSet.getString("job_type"),
                                resultSet.getString("queue_name"),
                                resultSet.getString("job_status"),
                                resultSet.getInt("attempt_count"),
                                resultSet.getInt("max_attempts"),
                                toInstant(resultSet, "available_at"),
                                resultSet.getString("locked_by"),
                                toNullableInstant(resultSet, "lease_expires_at"),
                                resultSet.getObject("execution_id", UUID.class),
                                resultSet.getString("execution_status"),
                                resultSet.getString("worker_id"),
                                getNullableInteger(resultSet, "attempt_number"),
                                toNullableInstant(resultSet, "started_at"),
                                toNullableInstant(resultSet, "finished_at"),
                                getNullableLong(resultSet, "duration_ms"),
                                resultSet.getString("error_type"),
                                resultSet.getString("error_message")
                        ))
                        .list();

        List<DashboardResponse.PerformancePoint> performance =
                jdbcClient.sql(PERFORMANCE_SQL)
                        .query((resultSet, rowNumber) ->
                                new DashboardResponse.PerformancePoint(
                                        toInstant(resultSet, "bucket"),
                                        resultSet.getLong("completed"),
                                        resultSet.getLong("p95_start_latency_ms")
                                ))
                        .list();

        return new DashboardResponse(
                generatedAt,
                summary,
                workers,
                recentJobs,
                performance
        );
    }

    private record CachedSnapshot(DashboardResponse response, Instant expiresAt) {
    }

    private static Instant toInstant(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant toNullableInstant(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        OffsetDateTime value = resultSet.getObject(
                column,
                OffsetDateTime.class
        );
        return value == null ? null : value.toInstant();
    }

    private static Integer getNullableInteger(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Long getNullableLong(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
