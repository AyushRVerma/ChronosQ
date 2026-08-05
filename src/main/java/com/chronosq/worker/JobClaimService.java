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

    private final JobExecutionRepository
            jobExecutionRepository;

    private final WorkerProperties
            workerProperties;


    @Transactional
    public List<ClaimedJob> claimAvailableJobs(
            Instant currentTime
    ) {

        Objects.requireNonNull(
                currentTime,
                "currentTime must not be null"
        );

        if (!workerProperties.enabled()) {
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
                        workerProperties
                                .claimBatchSize()
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

        return List.copyOf(results);
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