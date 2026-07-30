package com.chronosq.worker;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public final class WorkerNodeRowMapper
        implements RowMapper<WorkerNode> {

    @Override
    public WorkerNode mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {

        return new WorkerNode(
                resultSet.getString("worker_id"),

                resultSet.getString("instance_name"),

                WorkerStatus.valueOf(
                        resultSet.getString("status")
                ),

                readInstant(
                        resultSet,
                        "last_heartbeat_at"
                ),

                readInstant(
                        resultSet,
                        "started_at"
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