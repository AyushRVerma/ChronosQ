package com.chronosq.job.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.chronosq.api.JobApiMapper;
import com.chronosq.api.SubmitJobRequest;
import com.chronosq.job.domain.Job;
import com.chronosq.job.repository.JobRepository;
import com.chronosq.metrics.ChronosQMetrics;
import com.chronosq.scheduler.JobScheduleCalculator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobSubmissionService {

    private final JobRepository jobRepository;
    private final JobApiMapper jobApiMapper;
    private final ChronosQMetrics chronosQMetrics;

    private final JobScheduleCalculator jobScheduleCalculator;


    @Transactional
    public Job submit(
            SubmitJobRequest request
    ) {

        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        String idempotencyKey =
                normalizeIdempotencyKey(
                        request.idempotencyKey()
                );

        if (idempotencyKey != null) {

            var existingJob =
                    jobRepository
                            .findByIdempotencyKey(
                                    idempotencyKey
                            );

            if (existingJob.isPresent()) {
                return existingJob.get();
            }
        }

        Instant now = Instant.now();

        var scheduleDecision =
                jobScheduleCalculator
                        .calculateInitialSchedule(
                                request.scheduleType(),
                                request.availableAt(),
                                now
                        );

        Job newJob = new Job(
                UUID.randomUUID(),
                request.queueName().trim(),
                request.jobType().trim(),
                jobApiMapper.toPayloadString(
                        request.payload()
                ),
                scheduleDecision.initialStatus(),
                request.priorityOrDefault(),
                scheduleDecision.availableAt(),
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

        boolean inserted =
                jobRepository.save(newJob);

        if (inserted) {
            chronosQMetrics.incrementJobSubmitted();

            return newJob;
        }

        if (idempotencyKey != null) {

            return jobRepository
                    .findByIdempotencyKey(
                            idempotencyKey
                    )
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

    private String normalizeIdempotencyKey(
            String idempotencyKey
    ) {

        if (idempotencyKey == null) {
            return null;
        }

        String normalized =
                idempotencyKey.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }
}