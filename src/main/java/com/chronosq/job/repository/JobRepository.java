package com.chronosq.job.repository;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {

    boolean save(Job job);

    Optional<Job> findById(UUID jobId);

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    boolean updateStatus(
            UUID jobId,
            JobStatus expectedStatus,
            JobStatus newStatus,
            Instant updatedAt,
            Instant completedAt,
            long expectedVersion
    );
}