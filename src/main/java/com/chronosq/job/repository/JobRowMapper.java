package com.chronosq.job.repository;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

//It's a translator between database rows and Java objects.

@Component // tells Spring to manage this class, make it available for injection everywhere
public final class JobRowMapper implements RowMapper<Job> //RowMapper is a callback interface
// used to map each row of a ResultSet to a Java object.
// It is primarily utilized by the JdbcTemplate to execute SELECT queries and neatly convert database records into domain models,
// handling the iteration and database error-handling logic automatically
{
    @Override
    public Job mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new Job(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),

                resultSet.getString(
                        "queue_name"
                ),

                resultSet.getString(
                        "job_type"
                ),

                resultSet.getString(
                        "payload"
                ),

                JobStatus.valueOf(
                        resultSet.getString("status")
                ),

                resultSet.getInt(
                        "priority"
                ),

                readInstant(
                        resultSet,
                        "available_at"
                ),

                ScheduleType.valueOf(
                        resultSet.getString("schedule_type")
                ),

                resultSet.getObject(
                        "interval_seconds",
                        Long.class
                ),

                resultSet.getInt(
                        "attempt_count"
                ),

                resultSet.getInt(
                        "max_attempts"
                ),

                resultSet.getString(
                        "idempotency_key"
                ),

                resultSet.getString(
                        "locked_by"
                ),

                readInstant(
                        resultSet,
                        "lease_expires_at"
                ),

                resultSet.getInt(
                        "timeout_seconds"
                ),

                readInstant(
                        resultSet,
                        "created_at"
                ),

                readInstant(
                        resultSet,
                        "updated_at"
                ),

                readInstant(
                        resultSet,
                        "completed_at"
                ),

                resultSet.getLong(
                        "version"
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