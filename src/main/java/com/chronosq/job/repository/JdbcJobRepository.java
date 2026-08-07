package com.chronosq.job.repository;

import com.chronosq.job.domain.Job;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.chronosq.job.domain.JobStateMachine;
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

    private static final String PROMOTE_DUE_JOBS = """
            WITH due_jobs AS (
                SELECT id
                FROM jobs
              WHERE (status = :scheduledStatus
                      OR status = :retryWaitStatus)
                AND available_at <= :currentTime
                ORDER BY
                    available_at ASC,
                    priority DESC,
                    created_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE jobs AS job
            SET
                status = :readyStatus,
                updated_at = :currentTime,
                version = job.version + 1
            FROM due_jobs
            WHERE job.id = due_jobs.id
            """;

    private static final String CLAIM_READY_JOBS = """
            WITH claimable_jobs AS (
                SELECT id
                FROM jobs
                WHERE status = :readyStatus
                  AND queue_name = :queueName
                  AND available_at <= :currentTime
                ORDER BY
                    priority DESC,
                    created_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            ),
            claimed_jobs AS (
                UPDATE jobs AS job
                SET
                    status = :runningStatus,
                    locked_by = :workerId,
                    lease_expires_at = :leaseExpiresAt,
                    attempt_count =
                        job.attempt_count + 1,
                    updated_at = :currentTime,
                    version = job.version + 1
                FROM claimable_jobs
                WHERE job.id = claimable_jobs.id
                RETURNING job.*
            )
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
            FROM claimed_jobs
            ORDER BY
                priority DESC,
                created_at ASC
            """;

    private static final String FINISH_RUNNING_JOB = """
        UPDATE jobs
        SET
            status = :finalStatus,
            locked_by = NULL,
            lease_expires_at = NULL,
            updated_at = :updatedAt,
            completed_at = :completedAt,
            version = version + 1
        WHERE id = :jobId
          AND status = :runningStatus
          AND locked_by = :workerId
          AND version = :expectedVersion
        """;

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


    @Override
    public int promoteDueJobs(Instant currentTime, int batchSize) {

        Objects.requireNonNull(
                currentTime,
                "currentTime must not be null"
        );

        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "batchSize must be at least 1"
            );
        }

        return jdbcClient.sql(PROMOTE_DUE_JOBS)
                .param(
                        "scheduledStatus",
                        JobStatus.SCHEDULED.name()
                )
                .param(
                        "retryWaitStatus",
                        JobStatus.RETRY_WAIT.name()
                )
                .param(
                        "readyStatus",
                        JobStatus.READY.name()
                )
                .param(
                        "currentTime",
                       currentTime.atOffset(ZoneOffset.UTC)
                )
                .param(
                        "batchSize",
                        batchSize
                )
                .update();
    }


    @Override
    public List<Job> claimReadyJobs(
            String queueName,
            String workerId,
            Instant currentTime,
            Instant leaseExpiresAt,
            int batchSize
    ) {

        Objects.requireNonNull(
                queueName,
                "queueName must not be null"
        );

        Objects.requireNonNull(
                workerId,
                "workerId must not be null"
        );

        Objects.requireNonNull(
                currentTime,
                "currentTime must not be null"
        );

        Objects.requireNonNull(
                leaseExpiresAt,
                "leaseExpiresAt must not be null"
        );

        if (queueName.isBlank()) {
            throw new IllegalArgumentException(
                    "queueName must not be blank"
            );
        }

        if (workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "workerId must not be blank"
            );
        }

        if (!leaseExpiresAt.isAfter(currentTime)) {
            throw new IllegalArgumentException(
                    """
                            leaseExpiresAt must be \
                            after currentTime
                            """
            );
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "batchSize must be at least 1"
            );
        }

        return jdbcClient.sql(CLAIM_READY_JOBS)
                .param(
                        "readyStatus",
                        JobStatus.READY.name()
                )
                .param(
                        "runningStatus",
                        JobStatus.RUNNING.name()
                )
                .param(
                        "queueName",
                        queueName
                )
                .param(
                        "workerId",
                        workerId
                )
                .param(
                        "currentTime",
                       currentTime.atOffset(ZoneOffset.UTC)
                )
                .param(
                        "leaseExpiresAt",
                       leaseExpiresAt.atOffset(ZoneOffset.UTC)
                )
                .param(
                        "batchSize",
                        batchSize
                )
                .query(jobRowMapper)
                .list();
    }

    @Override
    public boolean finishRunningJob(
            UUID jobId,
            String workerId,
            JobStatus finalStatus,
            Instant updatedAt,
            Instant completedAt,
            long expectedVersion
    ) {

        Objects.requireNonNull(
                jobId,
                "jobId must not be null"
        );

        Objects.requireNonNull(
                workerId,
                "workerId must not be null"
        );

        Objects.requireNonNull(
                finalStatus,
                "finalStatus must not be null"
        );

        Objects.requireNonNull(
                updatedAt,
                "updatedAt must not be null"
        );

        if (workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "workerId must not be blank"
            );
        }

        JobStateMachine.validateTransition(
                JobStatus.RUNNING,
                finalStatus
        );

        int updatedRows =
                jdbcClient.sql(FINISH_RUNNING_JOB)
                        .param("jobId", jobId)
                        .param("workerId", workerId)
                        .param(
                                "runningStatus",
                                JobStatus.RUNNING.name()
                        )
                        .param(
                                "finalStatus",
                                finalStatus.name()
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

    @Override
    public boolean retryRunningJob(
            UUID jobId,
            String workerId,
            Instant retryAt,
            Instant updatedAt,
            long expectedVersion
    ) {
        JobStateMachine.validateTransition(
                JobStatus.RUNNING,
                JobStatus.RETRY_WAIT
        );

        int updatedRowCount = jdbcClient.sql(
                        """
                        UPDATE jobs
                        SET
                            status = :retryWaitStatus,
                            available_at = :retryAt,
                            locked_by = NULL,
                            lease_expires_at = NULL,
                            updated_at = :updatedAt,
                            completed_at = NULL,
                            version = version + 1
                        WHERE id = :jobId
                          AND status = :runningStatus
                          AND locked_by = :workerId
                          AND version = :expectedVersion
                        """
                )
                .param("retryWaitStatus", JobStatus.RETRY_WAIT.name())
                .param(
                        "retryAt",
                        toOffsetDateTime(retryAt)
                )
                .param(
                        "updatedAt",
                        toOffsetDateTime(updatedAt)
                )
                .param("jobId", jobId)
                .param("runningStatus", JobStatus.RUNNING.name())
                .param("workerId", workerId)
                .param("expectedVersion", expectedVersion)
                .update();

        return updatedRowCount == 1;
    }

    @Override
    public List<Job> findExpiredRunningJobs(
            Instant recoveryTime,
            int batchSize
    ) {
        return jdbcClient.sql(
                        """
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
                        WHERE status = :runningStatus
                          AND lease_expires_at <= :recoveryTime
                        ORDER BY lease_expires_at ASC
                        LIMIT :batchSize
                        FOR UPDATE SKIP LOCKED
                        """
                )
                .param(
                        "runningStatus",
                        JobStatus.RUNNING.name()
                )
                .param(
                        "recoveryTime",
                        recoveryTime.atOffset(ZoneOffset.UTC)
                )
                .param(
                        "batchSize",
                        batchSize
                )
                .query(jobRowMapper)
                .list();
    }

    @Override
    public boolean recoverExpiredRunningJob(
            UUID jobId,
            String workerId,
            JobStatus newStatus,
            Instant availableAt,
            Instant recoveryTime,
            long expectedVersion
    ) {
        if (newStatus != JobStatus.RETRY_WAIT
                && newStatus != JobStatus.DEAD_LETTERED) {

            throw new IllegalArgumentException(
                    "Recovered job status must be RETRY_WAIT "
                            + "or DEAD_LETTERED"
            );
        }

        JobStateMachine.validateTransition(
                JobStatus.RUNNING,
                newStatus
        );

        Instant completedAt =
                newStatus == JobStatus.DEAD_LETTERED
                        ? recoveryTime
                        : null;

        int updatedRowCount = jdbcClient.sql(
                        """
                        UPDATE jobs
                        SET
                            status = :newStatus,
                            available_at = :availableAt,
                            locked_by = NULL,
                            lease_expires_at = NULL,
                            updated_at = :recoveryTime,
                            completed_at = :completedAt,
                            version = version + 1
                        WHERE id = :jobId
                          AND status = :runningStatus
                          AND locked_by = :workerId
                          AND lease_expires_at <= :recoveryTime
                          AND version = :expectedVersion
                        """
                )
                .param(
                        "newStatus",
                        newStatus.name()
                )
                .param(
                        "availableAt",
                        toOffsetDateTime(availableAt)
                )
                .param(
                        "recoveryTime",
                        toOffsetDateTime(recoveryTime)
                )
                .param(
                        "completedAt",
                        toOffsetDateTime(completedAt)
                )
                .param(
                        "jobId",
                        jobId
                )
                .param(
                        "runningStatus",
                        JobStatus.RUNNING.name()
                )
                .param(
                        "workerId",
                        workerId
                )
                .param(
                        "expectedVersion",
                        expectedVersion
                )
                .update();

        return updatedRowCount == 1;
    }


}

//FOR UPDATE SKIP LOCKED allows multiple background workers to query
// and claim jobs from the same PostgreSQL table simultaneously without blocking
// each other and without taking duplicate jobs.

//Without SKIP LOCKED (Traditional Locking):
//Clerk A starts looking at tickets 1 to 5 to sell them. He locks them.
//Clerk B tries to look at tickets 1 to 5 too.
//Clerk B freezes and waits 🛑 until Clerk A finishes. Clerk B cannot serve any customers until Clerk A is completely done.
//Result: Slow lines, high waiting times, potential deadlocks.
//With SKIP LOCKED (Smart Queue Locking):
//Clerk A starts looking at tickets 1 to 5 and locks them.
//Clerk B tries to look at available tickets.
//PostgreSQL notices tickets 1 to 5 are locked, so it skips them instantly and hands tickets 6 to 10 to Clerk B!
//Clerk C arrives: sees 1-10 are locked, so he gets tickets 11 to 15!
//Result: All 3 clerks work simultaneously at full speed with zero waiting and zero duplicate tickets sold! ⚡