package com.chronosq.job.service;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobRequeue;
import com.chronosq.job.domain.JobStateMachine;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;
import com.chronosq.job.repository.JobRequeueRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobRequeueService {
    private final JobRepository jobRepository;
    private final JobRequeueRepository requeueRepository;

    public JobRequeueService(JobRepository jobRepository,
                             JobRequeueRepository requeueRepository) {
        this.jobRepository = jobRepository;
        this.requeueRepository = requeueRepository;
    }

    @Transactional
    public Result requeue(UUID sourceJobId, String actor, String reason) {
        Objects.requireNonNull(sourceJobId, "sourceJobId must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        String normalizedReason = reason.trim();
        if (normalizedReason.isEmpty() || normalizedReason.length() > 500) {
            throw new IllegalArgumentException("Requeue reason must contain 1–500 characters");
        }

        // Serialize operators requeueing the same source job, then reject a second copy.
        Job source = jobRepository.findByIdForUpdate(sourceJobId)
                .orElseThrow(() -> new JobNotFoundException(sourceJobId));
        if (!JobStateMachine.canRequeue(source.status())) {
            throw new JobNotRequeueableException(source.status());
        }
        if (requeueRepository.existsForSource(sourceJobId)) {
            throw new JobAlreadyRequeuedException(sourceJobId);
        }

        Instant now = Instant.now();
        // Retry this failed occurrence once, not its recurring definition.
        // The original cron chain may already have a future occurrence.
        Job fresh = new Job(
                UUID.randomUUID(), source.queueName(), source.jobType(),
                source.payload(), JobStatus.READY, source.priority(), now,
                ScheduleType.IMMEDIATE, null,
                null, null,
                null, 0, source.maxAttempts(),
                null, null, null, source.timeoutSeconds(),
                now, now, null, 0L);
        if (!jobRepository.save(fresh)) {
            throw new IllegalStateException("Requeued job could not be inserted");
        }

        JobRequeue event = new JobRequeue(UUID.randomUUID(), sourceJobId,
                fresh.id(), actor, normalizedReason, now);
        requeueRepository.save(event);
        return new Result(fresh, event);
    }

    @Transactional(readOnly = true)
    public List<JobRequeue> history(UUID sourceJobId) {
        if (jobRepository.findById(sourceJobId).isEmpty()) {
            throw new JobNotFoundException(sourceJobId);
        }
        return requeueRepository.findBySource(sourceJobId);
    }

    public record Result(Job job, JobRequeue audit) { }
}
