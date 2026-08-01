package com.chronosq.scheduler;

import java.time.Instant;
import java.util.Objects;

import com.chronosq.configuration
        .SchedulerProperties;

import com.chronosq.job.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation
        .Transactional;

@Service
@RequiredArgsConstructor
// ScheduledJobService is a Spring Service that acts as the coordinator
// for promoting overdue jobs from SCHEDULED ➔ READY.
public class ScheduledJobService {

    private final JobRepository jobRepository;

    private final SchedulerProperties schedulerProperties;


    @Transactional
    public int promoteDueJobs(
            Instant currentTime
    ) {

        Objects.requireNonNull(
                currentTime,
                "currentTime must not be null"
        );

        if (!schedulerProperties.enabled()) {
            return 0;
        }

        return jobRepository.promoteDueJobs(
                currentTime,
                schedulerProperties.batchSize()
        );
    }
}