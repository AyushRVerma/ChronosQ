package com.chronosq.job.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStateMachine;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// JobLifecycleService handles the business logic of
// moving a job from one state to another (e.g., SCHEDULED ➔ READY, or RUNNING ➔ SUCCEEDED).
public class JobLifecycleService {

    private final JobRepository jobRepository;


    @Transactional // Enforces that the entire method executes within a single database transaction.
                  // If any step fails or an exception is thrown, the transaction is rolled back completely.
    public Job transitionTo(
            UUID jobId,
            JobStatus newStatus
    ) {

        Objects.requireNonNull(
                jobId,
                "jobId must not be null"
        );

        Objects.requireNonNull(
                newStatus,
                "newStatus must not be null"
        );

        Job currentJob = jobRepository
                .findById(jobId)
                .orElseThrow(
                        () -> new JobNotFoundException(
                                jobId
                        )
                );

        JobStateMachine.validateTransition(
                currentJob.status(),
                newStatus
        );

        Instant now = Instant.now();

        //If the new status is a final state (SUCCEEDED, DEAD_LETTERED, CANCELLED),
        // completedAt gets set to now. Otherwise, it remains null.
        Instant completedAt =
                isTerminalStatus(newStatus)
                        ? now
                        : null;


        boolean updated = jobRepository.updateStatus(
                currentJob.id(),
                currentJob.status(),
                newStatus,
                now,
                completedAt,
                currentJob.version()
        );

        if (!updated) {
            throw new ConcurrentJobModificationException(
                    jobId
            );
        }

        return jobRepository
                .findById(jobId)
                .orElseThrow(
                        () -> new JobNotFoundException(
                                jobId
                        )
                );
    }

    private boolean isTerminalStatus(
            JobStatus status
    ) {

        return switch (status) {
            case SUCCEEDED,
                 DEAD_LETTERED,
                 CANCELLED -> true;

            default -> false;
        };
    }
}