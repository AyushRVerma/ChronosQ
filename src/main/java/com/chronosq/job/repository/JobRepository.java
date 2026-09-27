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

    Optional<Job> findByIdForUpdate(UUID jobId);

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

    List<Job> findDueSkippedCronJobs(
            Instant currentTime,
            int batchSize
    );

    boolean rescheduleSkippedCronJob(
            UUID jobId,
            Instant nextAvailableAt,
            Instant updatedAt,
            long expectedVersion
    );

    boolean promoteScheduledJob(
            UUID jobId,
            Instant updatedAt,
            long expectedVersion
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

    boolean retryRunningJob(
            UUID jobId,
            String workerId,
            Instant retryAt,
            Instant updatedAt,
            long expectedVersion
    );

    List<Job> findExpiredRunningJobs(
            Instant recoveryTime,
            int batchSize
    );

    boolean recoverExpiredRunningJob(
            UUID jobId,
            String workerId,
            JobStatus newStatus,
            Instant availableAt,
            Instant recoveryTime,
            long expectedVersion
    );

    boolean releaseUnstartedJob(
            UUID jobId,
            String workerId,
            Instant releasedAt,
            long expectedVersion
    );

    int extendLeasesForWorker(
            String workerId,
            Instant heartbeatTime,
            Instant leaseExpiresAt
    );


}
