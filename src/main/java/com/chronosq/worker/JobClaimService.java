package com.chronosq.worker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.chronosq.configuration.WorkerProperties;

import com.chronosq.execution.ExecutionStatus;
import com.chronosq.execution.JobExecution;
import com.chronosq.execution
        .JobExecutionRepository;

import com.chronosq.job.domain.Job;
import com.chronosq.job.repository.JobRepository;

import com.chronosq.metrics.ChronosQMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation
        .Transactional;

@Service
@RequiredArgsConstructor
// We are connecting JobRepository, JobExecutionRepository,
// WorkerProperties, and ClaimedJob into a single, cohesive service method

// JobClaimService is a Spring Service that claims ready jobs from PostgreSQL for a worker node and prepares them for execution.
public class JobClaimService {

    private final JobRepository jobRepository;

    private final ChronosQMetrics chronosQMetrics;

    private final JobExecutionRepository
            jobExecutionRepository;

    private final WorkerProperties
            workerProperties;


    @Transactional
    public List<ClaimedJob> claimAvailableJobs(
            Instant currentTime,
            int availableCapacity
    ) {

        Objects.requireNonNull(
                currentTime,
                "currentTime must not be null"
        );

        if (availableCapacity < 0) {
            throw new IllegalArgumentException(
                    "availableCapacity must not be negative"
            );
        }

        if (!workerProperties.enabled() || availableCapacity == 0) {
            return List.of();
        }

        Instant leaseExpiresAt =
                currentTime.plusSeconds(
                        workerProperties
                                .leaseDurationSeconds()
                );

        List<Job> claimedJobs =
                jobRepository.claimReadyJobs(
                        workerProperties.queueName(),
                        workerProperties.workerId(),
                        currentTime,
                        leaseExpiresAt,
                        Math.min(workerProperties.claimBatchSize(),
                                availableCapacity)
                );

        List<ClaimedJob> results =
                new ArrayList<>(
                        claimedJobs.size()
                );



        for (Job claimedJob : claimedJobs) {

            JobExecution execution =
                    createExecution(
                            claimedJob,
                            currentTime
                    );

            jobExecutionRepository.save(
                    execution
            );

            results.add(
                    new ClaimedJob(
                            claimedJob,
                            execution
                    )
            );
        }

        chronosQMetrics.incrementJobsClaimed(
                results.size()
        );

        return List.copyOf(results);
    }

    @Transactional
    public void releaseUnstartedJob(ClaimedJob claimedJob, Instant releasedAt) {
        Objects.requireNonNull(claimedJob, "claimedJob must not be null");
        Objects.requireNonNull(releasedAt, "releasedAt must not be null");

        // Dispatch was rejected before the handler started. Roll back the
        // claim's attempt number and remove its never-started execution.
        if (!jobRepository.releaseUnstartedJob(
                claimedJob.job().id(),
                claimedJob.job().lockedBy(),
                releasedAt,
                claimedJob.job().version())) {
            throw new IllegalStateException(
                    "Could not release unstarted job " + claimedJob.job().id()
            );
        }
        if (!jobExecutionRepository.deleteUnstartedExecution(
                claimedJob.execution().id(),
                claimedJob.job().id(),
                claimedJob.execution().workerId())) {
            throw new IllegalStateException(
                    "Could not remove unstarted execution "
                            + claimedJob.execution().id()
            );
        }
    }

    private JobExecution createExecution(
            Job claimedJob,
            Instant startedAt
    ) {

        return new JobExecution(
                UUID.randomUUID(),
                claimedJob.id(),
                workerProperties.workerId(),
                claimedJob.attemptCount(),
                ExecutionStatus.RUNNING,
                startedAt,
                null,
                null,
                null,
                null
        );
    }
}
