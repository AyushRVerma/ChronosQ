package com.chronosq.job.repository;

import com.chronosq.job.domain.Job;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.chronosq.job.domain.JobStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

 //A repository is the layer that talks directly to the database.
          // It's the only place in the codebase that knows SQL exists.


@Repository //@Repository annotation is a class-level stereotype annotation
// used to mark a Java class as a Data Access Object (DAO) or repository component.
// It serves two core purposes: it registers the class as a Spring-managed bean during classpath scanning,
// and it automatically translates database-specific exceptions into Spring’s unified DataAccessException hierarchy.
public class JdbcJobRepository implements JobRepository {


    //:id, :queueName = named parameters — Spring replaces these with actual values at runtime. Much safer than string concatenation:
    private static final String INSERT_JOB_SQL = """
            INSERT INTO jobs (
                id,
                queue_name,
                job_type,
                payload,
                status,
                priority,
                available_at,
                schedule_type,
                interval_seconds,
                attempt_count,
                max_attempts,
                idempotency_key,
                locked_by,
                lease_expires_at,
                timeout_seconds,
                created_at,
                updated_at,
                completed_at,
                version
            )
            VALUES (
                :id,
                :queueName,
                :jobType,
                CAST(:payload AS JSONB),
                :status,
                :priority,
                :availableAt,
                :scheduleType,
                :intervalSeconds,
                :attemptCount,
                :maxAttempts,
                :idempotencyKey,
                :lockedBy,
                :leaseExpiresAt,
                :timeoutSeconds,
                :createdAt,
                :updatedAt,
                :completedAt,
                :version
            )
             ON CONFLICT DO NOTHING
       \s""";

    private static final String FIND_BY_ID_SQL = """
            SELECT
                id,
                queue_name,
                job_type,
                payload,
                status,
                priority,
                available_at,
                schedule_type,
                interval_seconds,
                attempt_count,
                max_attempts,
                idempotency_key,
                locked_by,
                lease_expires_at,
                timeout_seconds,
                created_at,
                updated_at,
                completed_at,
                version
            FROM jobs
            WHERE id = :jobId
            """;

    private static final String FIND_BY_IDEMPOTENCY_KEY_SQL = """
            SELECT
                id,
                queue_name,
                job_type,
                payload,
                status,
                priority,
                available_at,
                schedule_type,
                interval_seconds,
                attempt_count,
                max_attempts,
                idempotency_key,
                locked_by,
                lease_expires_at,
                timeout_seconds,
                created_at,
                updated_at,
                completed_at,
                version
            FROM jobs
            WHERE idempotency_key = :idempotencyKey
            """;


    private static final String UPDATE_STATUS = """
        UPDATE jobs
        SET
            status = :newStatus,
            updated_at = :updatedAt,
            completed_at = :completedAt,
            version = version + 1
        WHERE id = :jobId
          AND status = :expectedStatus
          AND version = :expectedVersion
        """;

    private final JdbcClient jdbcClient; //
    private final JobRowMapper jobRowMapper;

    public JdbcJobRepository(
            JdbcClient jdbcClient,
            JobRowMapper jobRowMapper
    ) {
        this.jdbcClient = jdbcClient;
        this.jobRowMapper = jobRowMapper;
    }

    @Override
    public boolean save(Job job) {
        Objects.requireNonNull(
                job,
                "Job must not be null"
        );

        int insertedRows = jdbcClient
                .sql(INSERT_JOB_SQL)
                .param("id", job.id())
                .param("queueName", job.queueName())
                .param("jobType", job.jobType())
                .param("payload", job.payload())
                .param("status", job.status().name())
                .param("priority", job.priority())
                .param(
                        "availableAt",
                        toOffsetDateTime(job.availableAt())
                )
                .param(
                        "scheduleType",
                        job.scheduleType().name()
                )
                .param(
                        "intervalSeconds",
                        job.intervalSeconds()
                )
                .param(
                        "attemptCount",
                        job.attemptCount()
                )
                .param(
                        "maxAttempts",
                        job.maxAttempts()
                )
                .param(
                        "idempotencyKey",
                        job.idempotencyKey()
                )
                .param(
                        "lockedBy",
                        job.lockedBy()
                )
                .param(
                        "leaseExpiresAt",
                        toOffsetDateTime(job.leaseExpiresAt())
                )
                .param(
                        "timeoutSeconds",
                        job.timeoutSeconds()
                )
                .param(
                        "createdAt",
                        toOffsetDateTime(job.createdAt())
                )
                .param(
                        "updatedAt",
                        toOffsetDateTime(job.updatedAt())
                )
                .param(
                        "completedAt",
                        toOffsetDateTime(job.completedAt())
                )
                .param("version", job.version())
                .update();

//        if (insertedRows != 1) {
//            throw new IllegalStateException(
//                    "Expected to insert one job, but inserted "
//                            + insertedRows
//            );
//        }
        return insertedRows == 1;
    }

    @Override
    public Optional<Job> findById(UUID jobId) {
        Objects.requireNonNull(
                jobId,
                "Job ID must not be null"
        );

        return jdbcClient
                .sql(FIND_BY_ID_SQL)
                .param("jobId", jobId)
                .query(jobRowMapper) //uses JobRowMapper to convert row
                .optional();
    }

    @Override
    public Optional<Job> findByIdempotencyKey(
            String idempotencyKey
    ) {
        Objects.requireNonNull(
                idempotencyKey,
                "Idempotency key must not be null"
        );

        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key must not be blank"
            );
        }

        return jdbcClient
                .sql(FIND_BY_IDEMPOTENCY_KEY_SQL)
                .param("idempotencyKey", idempotencyKey)
                .query(jobRowMapper)
                .optional();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }

        return instant.atOffset(ZoneOffset.UTC);
    }

    @Override
    public boolean updateStatus(
            UUID jobId,
            JobStatus expectedStatus,
            JobStatus newStatus,
            Instant updatedAt,
            Instant completedAt,
            long expectedVersion
    ) {

        int updatedRows = jdbcClient.sql(UPDATE_STATUS)
                .param("jobId", jobId)
                .param(
                        "expectedStatus",
                        expectedStatus.name()
                )
                .param(
                        "newStatus",
                        newStatus.name()
                )
                .param(
                        "updatedAt",
                        toOffsetDateTime(updatedAt)
                )
                .param(
                        "completedAt",
                        toOffsetDateTime(completedAt)
                )
                .param(
                        "expectedVersion",
                        expectedVersion
                )
                .update();

        return updatedRows == 1;
    }
}