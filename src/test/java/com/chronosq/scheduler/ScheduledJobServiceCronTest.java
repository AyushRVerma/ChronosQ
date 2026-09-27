package com.chronosq.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.chronosq.configuration.SchedulerProperties;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.MissedExecutionPolicy;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

class ScheduledJobServiceCronTest {

    private final JobRepository repository = mock(JobRepository.class);
    private final JobScheduleCalculator calculator =
            new JobScheduleCalculator();
    private final ScheduledJobService service = new ScheduledJobService(
            repository,
            new SchedulerProperties(true, 1_000, 10),
            calculator
    );

    @Test
    void shouldSkipCronBacklogAndScheduleNextFutureFire() {
        Instant currentTime = Instant.parse("2026-01-01T10:30:00Z");
        Job job = cronJob(Instant.parse("2026-01-01T09:00:00Z"));
        when(repository.findDueSkippedCronJobs(currentTime, 10))
                .thenReturn(List.of(job));
        when(repository.rescheduleSkippedCronJob(
                job.id(),
                Instant.parse("2026-01-01T11:00:00Z"),
                currentTime,
                0L
        )).thenReturn(true);
        when(repository.promoteDueJobs(currentTime, 9)).thenReturn(2);

        assertThat(service.promoteDueJobs(currentTime)).isEqualTo(2);
        verify(repository).rescheduleSkippedCronJob(
                job.id(),
                Instant.parse("2026-01-01T11:00:00Z"),
                currentTime,
                0L
        );
    }

    @Test
    void shouldPromoteSingleDueFireForSkipPolicy() {
        Instant currentTime = Instant.parse("2026-01-01T10:00:01Z");
        Job job = cronJob(Instant.parse("2026-01-01T10:00:00Z"));
        when(repository.findDueSkippedCronJobs(currentTime, 10))
                .thenReturn(List.of(job));
        when(repository.promoteScheduledJob(
                job.id(), currentTime, 0L
        )).thenReturn(true);

        assertThat(service.promoteDueJobs(currentTime)).isEqualTo(1);
        verify(repository).promoteScheduledJob(job.id(), currentTime, 0L);
    }

    private Job cronJob(Instant availableAt) {
        return new Job(
                UUID.randomUUID(),
                "default",
                "PRINT_MESSAGE",
                "{\"message\":\"hello\"}",
                JobStatus.SCHEDULED,
                0,
                availableAt,
                ScheduleType.CRON,
                null,
                "0 0 * * * *",
                "UTC",
                MissedExecutionPolicy.SKIP,
                0,
                3,
                null,
                null,
                null,
                30,
                availableAt,
                availableAt,
                null,
                0L
        );
    }
}
