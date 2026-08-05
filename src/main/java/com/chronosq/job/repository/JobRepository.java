package com.chronosq.job.repository;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;

import java.time.Instant;
import java.util.List;
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

    int promoteDueJobs(
            Instant currentTime,
            int batchSize
    );

    List<Job> claimReadyJobs(
            String queueName,
            String workerId,
            Instant currentTime,
            Instant leaseExpiresAt,
            int batchSize
    );

    boolean finishRunningJob(
            UUID jobId,
            String workerId,
            JobStatus finalStatus,
            Instant updatedAt,
            Instant completedAt,
            long expectedVersion
    );
}