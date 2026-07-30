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
}