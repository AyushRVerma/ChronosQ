package com.chronosq.job.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStateMachine;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.repository.JobRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobLifecycleService {

    private final JobRepository jobRepository;

    public JobLifecycleService(
            JobRepository jobRepository
    ) {
        this.jobRepository = jobRepository;
    }

    @Transactional
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