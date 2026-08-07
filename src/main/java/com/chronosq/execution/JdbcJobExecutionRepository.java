package com.chronosq.execution;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;


//JdbcJobExecutionRepository is the database access component responsible
//for reading and writing to the job_executions table in PostgreSQL.
@Repository
public class JdbcJobExecutionRepository
        implements JobExecutionRepository {

    private static final String INSERT_EXECUTION_SQL = """
            INSERT INTO job_executions (
                id,
                job_id,
                worker_id,
                attempt_number,
                status,
                started_at,
                finished_at,
                duration_ms,
                error_type,
                error_message
            )
            VALUES (
                :id,
                :jobId,
                :workerId,
                :attemptNumber,
                :status,
                :startedAt,
                :finishedAt,
                :durationMs,
                :errorType,
                :errorMessage
            )
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT
                id,
                job_id,
                worker_id,
                attempt_number,
                status,
                started_at,
                finished_at,
                duration_ms,
                error_type,
                error_message
            FROM job_executions
            WHERE id = :executionId
            """;

    private static final String FIND_BY_JOB_ID_SQL = """
            SELECT
                id,
                job_id,
                worker_id,
                attempt_number,
                status,
                started_at,
                finished_at,
                duration_ms,
                error_type,
                error_message
            FROM job_executions
            WHERE job_id = :jobId
            ORDER BY attempt_number ASC
            """;

    private static final String FINALIZE_EXECUTION = """
        UPDATE job_executions
        SET
            status = :finalStatus,
            finished_at = :finishedAt,
            duration_ms = :durationMs,
            error_type = :errorType,
            error_message = :errorMessage
        WHERE id = :executionId
          AND worker_id = :workerId
          AND status = :runningStatus
        """;

    private final JdbcClient jdbcClient;
    private final JobExecutionRowMapper rowMapper;

    public JdbcJobExecutionRepository(
            JdbcClient jdbcClient,
            JobExecutionRowMapper rowMapper
    ) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = rowMapper;
    }

    @Override
    public void save(JobExecution execution) {
        Objects.requireNonNull(
                execution,
                "Job execution must not be null"
        );

        int insertedRows = jdbcClient
                .sql(INSERT_EXECUTION_SQL)
                .param("id", execution.id())
                .param("jobId", execution.jobId())
                .param(
                        "workerId",
                        execution.workerId()
                )
                .param(
                        "attemptNumber",
                        execution.attemptNumber()
                )
                .param(
                        "status",
                        execution.status().name()
                )
                .param(
                        "startedAt",
                        toOffsetDateTime(
                                execution.startedAt()
                        )
                )
                .param(
                        "finishedAt",
                        toOffsetDateTime(
                                execution.finishedAt()
                        )
                )
                .param(
                        "durationMs",
                        execution.durationMs()
                )
                .param(
                        "errorType",
                        execution.errorType()
                )
                .param(
                        "errorMessage",
                        execution.errorMessage()
                )
                .update();

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "Expected to insert one execution, but inserted "
                            + insertedRows
            );
        }
    }

    @Override
    public Optional<JobExecution> findById(
            UUID executionId
    ) {
        Objects.requireNonNull(
                executionId,
                "Execution ID must not be null"
        );

        return jdbcClient
                .sql(FIND_BY_ID_SQL)
                .param(
                        "executionId",
                        executionId
                )
                .query(rowMapper)
                .optional();
    }

    @Override
    public List<JobExecution> findByJobId(
            UUID jobId
    ) {
        Objects.requireNonNull(
                jobId,
                "Job ID must not be null"
        );

        return jdbcClient
                .sql(FIND_BY_JOB_ID_SQL)
                .param("jobId", jobId)
                .query(rowMapper)
                .list();
    }

    private OffsetDateTime toOffsetDateTime(
            Instant instant
    ) {
        if (instant == null) {
            return null;
        }

        return instant.atOffset(ZoneOffset.UTC);
    }

    @Override
    public boolean finalizeExecution(
            UUID executionId,
            String workerId,
            ExecutionResult result
    ) {

        Objects.requireNonNull(
                executionId,
                "executionId must not be null"
        );

        Objects.requireNonNull(
                workerId,
                "workerId must not be null"
        );

        Objects.requireNonNull(
                result,
                "result must not be null"
        );

        if (workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "workerId must not be blank"
            );
        }

        int updatedRows = jdbcClient.sql(FINALIZE_EXECUTION)
                .param("executionId", executionId)
                .param("workerId", workerId)
                .param(
                        "runningStatus",
                        ExecutionStatus.RUNNING.name()
                )
                .param(
                        "finalStatus",
                        result.status().name()
                )
                .param(
                        "finishedAt",

                                result.finishedAt().atOffset(ZoneOffset.UTC)

                )
                .param(
                        "durationMs",
                        result.durationMs()
                )
                .param(
                        "errorType",
                        result.errorType()
                )
                .param(
                        "errorMessage",
                        result.errorMessage()
                )
                .update();

        return updatedRows == 1;
    }
    @Override
    public boolean abandonRunningExecution(
            UUID jobId,
            String workerId,
            Instant recoveredAt
    ) {
        int updatedRowCount = jdbcClient.sql(
                        """
                        UPDATE job_executions
                        SET
                            status = :abandonedStatus,
                            finished_at = :recoveredAt,
                            duration_ms = NULL,
                            error_type = :errorType,
                            error_message = :errorMessage
                        WHERE job_id = :jobId
                          AND worker_id = :workerId
                          AND status = :runningStatus
                        """
                )
                .param(
                        "abandonedStatus",
                        ExecutionStatus.ABANDONED.name()
                )
                .param(
                        "recoveredAt",
                        recoveredAt.atOffset(ZoneOffset.UTC)
                )
                .param(
                        "errorType",
                        "LEASE_EXPIRED"
                )
                .param(
                        "errorMessage",
                        "Worker lease expired before "
                                + "execution completed"
                )
                .param(
                        "jobId",
                        jobId
                )
                .param(
                        "workerId",
                        workerId
                )
                .param(
                        "runningStatus",
                        ExecutionStatus.RUNNING.name()
                )
                .update();

        return updatedRowCount == 1;
    }
}