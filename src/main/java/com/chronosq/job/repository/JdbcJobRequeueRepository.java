package com.chronosq.job.repository;

import com.chronosq.job.domain.JobRequeue;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcJobRequeueRepository implements JobRequeueRepository {
    private final JdbcClient jdbcClient;

    public JdbcJobRequeueRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean existsForSource(UUID sourceJobId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM job_requeues WHERE source_job_id = :sourceJobId)")
                .param("sourceJobId", sourceJobId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void save(JobRequeue requeue) {
        jdbcClient.sql("""
                INSERT INTO job_requeues (id, source_job_id, new_job_id, actor, reason, created_at)
                VALUES (:id, :sourceJobId, :newJobId, :actor, :reason, :createdAt)
                """)
                .param("id", requeue.id())
                .param("sourceJobId", requeue.sourceJobId())
                .param("newJobId", requeue.newJobId())
                .param("actor", requeue.actor())
                .param("reason", requeue.reason())
                .param("createdAt", requeue.createdAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<JobRequeue> findBySource(UUID sourceJobId) {
        return jdbcClient.sql("""
                SELECT id, source_job_id, new_job_id, actor, reason, created_at
                FROM job_requeues
                WHERE source_job_id = :sourceJobId
                ORDER BY created_at DESC
                """)
                .param("sourceJobId", sourceJobId)
                .query((rs, rowNum) -> new JobRequeue(
                        rs.getObject("id", UUID.class),
                        rs.getObject("source_job_id", UUID.class),
                        rs.getObject("new_job_id", UUID.class),
                        rs.getString("actor"),
                        rs.getString("reason"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
