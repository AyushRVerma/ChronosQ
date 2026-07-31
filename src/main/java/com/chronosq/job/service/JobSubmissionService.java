package com.chronosq.job.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.chronosq.api.JobApiMapper;
import com.chronosq.api.SubmitJobRequest;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

// This class ties together everything we've discussed so far about
// job creation, idempotency, initial status calculation, and duplicate handling!
public class JobSubmissionService {

    private final JobRepository jobRepository;
    private final JobApiMapper jobApiMapper;



    @Transactional
    public Job submit(SubmitJobRequest request) {

        Objects.requireNonNull(request, "request must not be null");

        //Trims whitespace from the idempotency key and converts empty strings "" to null
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());

        //Fast Lookup: If an idempotency key is provided and already exists
        // in the database, it skips creation completely and returns the previously saved job.
        if (idempotencyKey != null) {

            var existingJob = jobRepository.findByIdempotencyKey(idempotencyKey);

            if (existingJob.isPresent()) {
                return existingJob.get(); //  Return existing job immediately
            }
        }

        Instant now = Instant.now();

        Instant availableAt = calculateAvailableAt(request, now);

        JobStatus initialStatus =calculateInitialStatus( request.scheduleType(), availableAt, now);

        Job newJob = new Job(
                UUID.randomUUID(),
                request.queueName().trim(),
                request.jobType().trim(),
                jobApiMapper.toPayloadString(
                        request.payload()
                ),
                initialStatus,
                request.priorityOrDefault(),
                availableAt,
                request.scheduleType(),
                request.intervalSeconds(),
                0,
                request.maxAttemptsOrDefault(),
                idempotencyKey,
                null,
                null,
                request.timeoutSecondsOrDefault(),
                now,
                now,
                null,
                0L
        );

        boolean inserted = jobRepository.save(newJob);

        if (inserted) {
            return newJob;
        }

        //Concurrent Race Condition
        if (idempotencyKey != null) {

            return jobRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    """
                                    Job insert conflicted, but the \
                                    existing idempotent job could not \
                                    be found
                                    """
                            )
                    );
        }

        throw new IllegalStateException(
                "Job could not be inserted"
        );
    }

    private Instant calculateAvailableAt(SubmitJobRequest request, Instant now) {

        if (request.scheduleType()
                == ScheduleType.IMMEDIATE) {

            return now;
        }

        if (request.availableAt() == null) {
            return now;
        }

        return request.availableAt();
    }

    private JobStatus calculateInitialStatus(
            ScheduleType scheduleType,
            Instant availableAt,
            Instant now
    ) {

        if (scheduleType
                == ScheduleType.IMMEDIATE) {

            return JobStatus.READY;
        }

        if (availableAt.isAfter(now)) {
            return JobStatus.SCHEDULED;
        }

        return JobStatus.READY;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {

        if (idempotencyKey == null) {
            return null;
        }

        String normalized = idempotencyKey.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }
}