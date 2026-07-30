package com.chronosq.execution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

//This class follows the exact same pattern as the JobRowMapper we looked at earlier,
// but this time it maps database rows from the job_executions table to the JobExecution Java record.

@Component
public final class JobExecutionRowMapper
        implements RowMapper<JobExecution> {

    @Override
    public JobExecution mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new JobExecution(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getObject(
                        "job_id",
                        UUID.class
                ),

                resultSet.getString(
                        "worker_id"
                ),

                resultSet.getInt(
                        "attempt_number"
                ),

                ExecutionStatus.valueOf(
                        resultSet.getString("status")
                ),

                readInstant(
                        resultSet,
                        "started_at"
                ),

                readInstant(
                        resultSet,
                        "finished_at"
                ),

                resultSet.getObject(
                        "duration_ms",
                        Long.class
                ),

                resultSet.getString(
                        "error_type"
                ),

                resultSet.getString(
                        "error_message"
                )
        );
    }

    private Instant readInstant(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {

        OffsetDateTime value = resultSet.getObject(
                columnName,
                OffsetDateTime.class
        );

        if (value == null) {
            return null;
        }

        return value.toInstant();
    }
}