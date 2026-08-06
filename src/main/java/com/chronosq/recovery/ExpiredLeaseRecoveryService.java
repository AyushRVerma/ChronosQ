package com.chronosq.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronosq.configuration.RecoveryProperties;
import com.chronosq.execution.JobExecutionRepository;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.repository.JobRepository;

@Service
public class ExpiredLeaseRecoveryService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final RetryPolicy retryPolicy;
    private final RecoveryProperties recoveryProperties;

    public ExpiredLeaseRecoveryService(
            JobRepository jobRepository,
            JobExecutionRepository jobExecutionRepository,
            RetryPolicy retryPolicy,
            RecoveryProperties recoveryProperties
    ) {
        this.jobRepository = Objects.requireNonNull(
                jobRepository,
                "jobRepository must not be null"
        );

        this.jobExecutionRepository = Objects.requireNonNull(
                jobExecutionRepository,
                "jobExecutionRepository must not be null"
        );

        this.retryPolicy = Objects.requireNonNull(
                retryPolicy,
                "retryPolicy must not be null"
        );

        this.recoveryProperties = Objects.requireNonNull(
                recoveryProperties,
                "recoveryProperties must not be null"
        );
    }

    @Transactional
    public RecoveryResult recoverExpiredJobs(
            Instant recoveryTime
    ) {
        Objects.requireNonNull(
                recoveryTime,
                "recoveryTime must not be null"
        );

        if (!recoveryProperties.enabled()) {
            return RecoveryResult.empty();
        }

        List<Job> expiredJobs =
                jobRepository.findExpiredRunningJobs(
                        recoveryTime,
                        recoveryProperties.batchSize()
                );

        int retriedJobCount = 0;
        int deadLetteredJobCount = 0;

        for (Job expiredJob : expiredJobs) {
            recoverExpiredJob(
                    expiredJob,
                    recoveryTime
            );

            if (retryPolicy.canRetry(expiredJob)) {
                retriedJobCount++;
            } else {
                deadLetteredJobCount++;
            }
        }

        return new RecoveryResult(
                retriedJobCount,
                deadLetteredJobCount
        );
    }

    private void recoverExpiredJob(
            Job expiredJob,
            Instant recoveryTime
    ) {
        String workerId = Objects.requireNonNull(
                expiredJob.lockedBy(),
                "Expired running job must have a worker ID"
        );

        boolean executionAbandoned =
                jobExecutionRepository.abandonRunningExecution(
                        expiredJob.id(),
                        workerId,
                        recoveryTime
                );

        if (!executionAbandoned) {
            throw new IllegalStateException(
                    "Could not abandon running execution for job "
                            + expiredJob.id()
            );
        }

        boolean canRetry =
                retryPolicy.canRetry(expiredJob);

        JobStatus recoveredStatus = canRetry
                ? JobStatus.RETRY_WAIT
                : JobStatus.DEAD_LETTERED;

        Instant availableAt = canRetry
                ? retryPolicy.calculateNextRetryAt(
                expiredJob,
                recoveryTime
        )
                : recoveryTime;

        boolean jobRecovered =
                jobRepository.recoverExpiredRunningJob(
                        expiredJob.id(),
                        workerId,
                        recoveredStatus,
                        availableAt,
                        recoveryTime,
                        expiredJob.version()
                );

        if (!jobRecovered) {
            throw new IllegalStateException(
                    "Could not recover expired job "
                            + expiredJob.id()
            );
        }
    }
}