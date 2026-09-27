package com.chronosq.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.MissedExecutionPolicy;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;
import com.chronosq.job.repository.JobRequeueRepository;

@ExtendWith(MockitoExtension.class)
class JobRequeueServiceTest {

    @Mock JobRepository jobRepository;
    @Mock JobRequeueRepository requeueRepository;

    @Test
    void requeuedCronOccurrenceDoesNotStartAnotherCronChain() {
        assertRequeuedAsOneOff(ScheduleType.CRON);
    }

    @Test
    void requeuedFixedIntervalOccurrenceDoesNotStartAnotherIntervalChain() {
        assertRequeuedAsOneOff(ScheduleType.FIXED_INTERVAL);
    }

    private void assertRequeuedAsOneOff(ScheduleType scheduleType) {
        UUID sourceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-26T09:00:00Z");
        Job source = new Job(
                sourceId, "default", "PRINT_MESSAGE", "{\"message\":\"retry\"}",
                JobStatus.DEAD_LETTERED, 3, now, scheduleType,
                scheduleType == ScheduleType.FIXED_INTERVAL ? 300L : null,
                scheduleType == ScheduleType.CRON ? "0 0 9 * * *" : null,
                scheduleType == ScheduleType.CRON ? "UTC" : null,
                scheduleType == ScheduleType.CRON ? MissedExecutionPolicy.SKIP : null,
                3, 3, null, null, null, 30, now, now, now, 1L
        );
        when(jobRepository.findByIdForUpdate(sourceId))
                .thenReturn(Optional.of(source));
        when(jobRepository.save(any(Job.class))).thenReturn(true);

        JobRequeueService.Result result = new JobRequeueService(
                jobRepository, requeueRepository
        ).requeue(sourceId, "operator", "retry one occurrence");

        Job retry = result.job();
        assertThat(retry.id()).isNotEqualTo(sourceId);
        assertThat(retry.status()).isEqualTo(JobStatus.READY);
        assertThat(retry.scheduleType()).isEqualTo(ScheduleType.IMMEDIATE);
        assertThat(retry.intervalSeconds()).isNull();
        assertThat(retry.cronExpression()).isNull();
        assertThat(retry.cronTimeZone()).isNull();
        assertThat(retry.missedExecutionPolicy()).isNull();
        assertThat(retry.payload()).isEqualTo(source.payload());
        assertThat(retry.attemptCount()).isZero();
        assertThat(retry.maxAttempts()).isEqualTo(source.maxAttempts());
        assertThat(result.audit().sourceJobId()).isEqualTo(sourceId);
    }
}
