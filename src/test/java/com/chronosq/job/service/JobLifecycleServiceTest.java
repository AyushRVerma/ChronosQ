package com.chronosq.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.chronosq.job.domain.InvalidJobStateTransitionException;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class JobLifecycleServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobLifecycleService jobLifecycleService;

    @BeforeEach
    void setUp() {

        MockitoAnnotations.openMocks(this);

        jobLifecycleService =
                new JobLifecycleService(jobRepository);
    }

    @Test
    void shouldTransitionReadyJobToRunning() {

        UUID jobId = UUID.randomUUID();

        Job readyJob = createJob(
                jobId,
                JobStatus.READY,
                0L,
                null
        );

        Job runningJob = createJob(
                jobId,
                JobStatus.RUNNING,
                1L,
                null
        );

        when(jobRepository.findById(jobId))
                .thenReturn(
                        Optional.of(readyJob),
                        Optional.of(runningJob)
                );

        when(jobRepository.updateStatus(
                eq(jobId),
                eq(JobStatus.READY),
                eq(JobStatus.RUNNING),
                any(Instant.class),
                isNull(),
                eq(0L)
        )).thenReturn(true);

        Job result = jobLifecycleService.transitionTo(
                jobId,
                JobStatus.RUNNING
        );

        assertThat(result.status())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(result.version())
                .isEqualTo(1L);

        verify(jobRepository).updateStatus(
                eq(jobId),
                eq(JobStatus.READY),
                eq(JobStatus.RUNNING),
                any(Instant.class),
                isNull(),
                eq(0L)
        );
    }

    @Test
    void shouldSetCompletionTimeForTerminalStatus() {

        UUID jobId = UUID.randomUUID();

        Job runningJob = createJob(
                jobId,
                JobStatus.RUNNING,
                0L,
                null
        );

        Job succeededJob = createJob(
                jobId,
                JobStatus.SUCCEEDED,
                1L,
                Instant.parse(
                        "2026-01-01T10:05:00Z"
                )
        );

        when(jobRepository.findById(jobId))
                .thenReturn(
                        Optional.of(runningJob),
                        Optional.of(succeededJob)
                );

        when(jobRepository.updateStatus(
                eq(jobId),
                eq(JobStatus.RUNNING),
                eq(JobStatus.SUCCEEDED),
                any(Instant.class),
                any(Instant.class),
                eq(0L)
        )).thenReturn(true);

        jobLifecycleService.transitionTo(
                jobId,
                JobStatus.SUCCEEDED
        );

        ArgumentCaptor<Instant> updatedAtCaptor =
                ArgumentCaptor.forClass(Instant.class);

        ArgumentCaptor<Instant> completedAtCaptor =
                ArgumentCaptor.forClass(Instant.class);

        verify(jobRepository).updateStatus(
                eq(jobId),
                eq(JobStatus.RUNNING),
                eq(JobStatus.SUCCEEDED),
                updatedAtCaptor.capture(),
                completedAtCaptor.capture(),
                eq(0L)
        );

        assertThat(completedAtCaptor.getValue())
                .isNotNull();

        assertThat(completedAtCaptor.getValue())
                .isEqualTo(
                        updatedAtCaptor.getValue()
                );
    }

    @Test
    void shouldThrowWhenJobDoesNotExist() {

        UUID jobId = UUID.randomUUID();

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> jobLifecycleService.transitionTo(
                        jobId,
                        JobStatus.RUNNING
                )
        )
                .isInstanceOf(
                        JobNotFoundException.class
                )
                .hasMessageContaining(
                        jobId.toString()
                );

        verify(jobRepository, never()).updateStatus(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyLong()
        );
    }

    @Test
    void shouldRejectInvalidStateTransition() {

        UUID jobId = UUID.randomUUID();

        Job readyJob = createJob(
                jobId,
                JobStatus.READY,
                0L,
                null
        );

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(readyJob));

        assertThatThrownBy(
                () -> jobLifecycleService.transitionTo(
                        jobId,
                        JobStatus.SUCCEEDED
                )
        )
                .isInstanceOf(
                        InvalidJobStateTransitionException.class
                );

        verify(jobRepository, never()).updateStatus(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyLong()
        );
    }

    @Test
    void shouldDetectConcurrentModification() {

        UUID jobId = UUID.randomUUID();

        Job readyJob = createJob(
                jobId,
                JobStatus.READY,
                0L,
                null
        );

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(readyJob));

        when(jobRepository.updateStatus(
                eq(jobId),
                eq(JobStatus.READY),
                eq(JobStatus.RUNNING),
                any(Instant.class),
                isNull(),
                eq(0L)
        )).thenReturn(false);

        assertThatThrownBy(
                () -> jobLifecycleService.transitionTo(
                        jobId,
                        JobStatus.RUNNING
                )
        )
                .isInstanceOf(
                        ConcurrentJobModificationException.class
                )
                .hasMessageContaining(
                        jobId.toString()
                );
    }

    @Test
    void shouldRejectNullJobId() {

        assertThatThrownBy(
                () -> jobLifecycleService.transitionTo(
                        null,
                        JobStatus.RUNNING
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "jobId must not be null"
                );
    }

    @Test
    void shouldRejectNullNewStatus() {

        assertThatThrownBy(
                () -> jobLifecycleService.transitionTo(
                        UUID.randomUUID(),
                        null
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "newStatus must not be null"
                );
    }

    private Job createJob(
            UUID jobId,
            JobStatus status,
            long version,
            Instant completedAt
    ) {

        Instant now =
                Instant.parse("2026-01-01T10:00:00Z");

        return new Job(
                jobId,
                "default",
                "SEND_EMAIL",
                """
                {
                  "email": "learner@example.com"
                }
                """,
                status,
                5,
                now,
                ScheduleType.IMMEDIATE,
                null,
                0,
                3,
                null,
                null,
                null,
                30,
                now,
                now,
                completedAt,
                version
        );
    }
}