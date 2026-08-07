package com.chronosq.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.chronosq.api.JobApiMapper;
import com.chronosq.api.SubmitJobRequest;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

import com.chronosq.metrics.ChronosQMetrics;
import com.chronosq.scheduler.JobScheduleCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.JsonNode;

@ExtendWith(MockitoExtension.class)
class JobSubmissionServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobApiMapper jobApiMapper;

    @Mock
    private JsonNode payload;

    @Mock
    private ChronosQMetrics chronosQMetrics;

    @Test
    void shouldCreateImmediateJobWithDefaults() {

        JobSubmissionService service =
                createService();

        SubmitJobRequest request =
                new SubmitJobRequest(
                        " default ",
                        " PRINT_MESSAGE ",
                        payload,
                        null,
                        null,
                        ScheduleType.IMMEDIATE,
                        null,
                        null,
                        null,
                        null
                );

        when(jobApiMapper.toPayloadString(payload))
                .thenReturn(
                        """
                        {"message":"Hello"}
                        """
                );

        when(jobRepository.save(any(Job.class)))
                .thenReturn(true);

        Job result = service.submit(request);

        ArgumentCaptor<Job> jobCaptor =
                ArgumentCaptor.forClass(Job.class);

        verify(jobRepository).save(
                jobCaptor.capture()
        );

        Job savedJob = jobCaptor.getValue();

        assertThat(result)
                .isEqualTo(savedJob);

        assertThat(savedJob.id())
                .isNotNull();

        assertThat(savedJob.queueName())
                .isEqualTo("default");

        assertThat(savedJob.jobType())
                .isEqualTo("PRINT_MESSAGE");

        assertThat(savedJob.payload())
                .isEqualTo(
                        """
                        {"message":"Hello"}
                        """
                );

        assertThat(savedJob.status())
                .isEqualTo(JobStatus.READY);

        assertThat(savedJob.priority())
                .isZero();

        assertThat(savedJob.attemptCount())
                .isZero();

        assertThat(savedJob.maxAttempts())
                .isEqualTo(3);

        assertThat(savedJob.timeoutSeconds())
                .isEqualTo(30);

        assertThat(savedJob.version())
                .isZero();

        assertThat(savedJob.availableAt())
                .isNotNull();

        assertThat(savedJob.createdAt())
                .isEqualTo(savedJob.updatedAt());
    }

    @Test
    void shouldCreateFutureOneTimeJobAsScheduled() {

        JobSubmissionService service =
                createService();

        Instant futureTime =
                Instant.parse(
                        "2099-01-01T10:00:00Z"
                );

        SubmitJobRequest request =
                new SubmitJobRequest(
                        "notifications",
                        "PRINT_MESSAGE",
                        payload,
                        10,
                        futureTime,
                        ScheduleType.ONE_TIME,
                        null,
                        5,
                        60,
                        null
                );

        when(jobApiMapper.toPayloadString(payload))
                .thenReturn(
                        """
                        {"message":"Run later"}
                        """
                );

        when(jobRepository.save(any(Job.class)))
                .thenReturn(true);

        Job result = service.submit(request);

        assertThat(result.status())
                .isEqualTo(JobStatus.SCHEDULED);

        assertThat(result.availableAt())
                .isEqualTo(futureTime);

        assertThat(result.priority())
                .isEqualTo(10);

        assertThat(result.maxAttempts())
                .isEqualTo(5);

        assertThat(result.timeoutSeconds())
                .isEqualTo(60);
    }

    @Test
    void shouldCreateFixedIntervalJob() {

        JobSubmissionService service =
                createService();

        SubmitJobRequest request =
                new SubmitJobRequest(
                        "maintenance",
                        "HTTP_WEBHOOK",
                        payload,
                        5,
                        null,
                        ScheduleType.FIXED_INTERVAL,
                        300L,
                        3,
                        30,
                        null
                );

        when(jobApiMapper.toPayloadString(payload))
                .thenReturn(
                        """
                        {
                          "url": "https://example.com"
                        }
                        """
                );

        when(jobRepository.save(any(Job.class)))
                .thenReturn(true);

        Job result = service.submit(request);

        assertThat(result.status())
                .isEqualTo(JobStatus.READY);

        assertThat(result.scheduleType())
                .isEqualTo(
                        ScheduleType.FIXED_INTERVAL
                );

        assertThat(result.intervalSeconds())
                .isEqualTo(300L);
    }

    @Test
    void shouldReturnExistingIdempotentJob() {

        JobSubmissionService service =
                createService();

        Job existingJob = createExistingJob(
                "request-101"
        );

        SubmitJobRequest request =
                new SubmitJobRequest(
                        "default",
                        "PRINT_MESSAGE",
                        payload,
                        0,
                        null,
                        ScheduleType.IMMEDIATE,
                        null,
                        3,
                        30,
                        " request-101 "
                );

        when(
                jobRepository.findByIdempotencyKey(
                        "request-101"
                )
        ).thenReturn(
                Optional.of(existingJob)
        );

        Job result = service.submit(request);

        assertThat(result)
                .isSameAs(existingJob);

        verify(jobRepository, never())
                .save(any(Job.class));

        verify(jobApiMapper, never())
                .toPayloadString(any());
    }

    @Test
    void shouldReturnExistingJobAfterConcurrentInsert() {

        JobSubmissionService service =
                createService();

        Job existingJob = createExistingJob(
                "request-202"
        );

        SubmitJobRequest request =
                new SubmitJobRequest(
                        "default",
                        "PRINT_MESSAGE",
                        payload,
                        0,
                        null,
                        ScheduleType.IMMEDIATE,
                        null,
                        3,
                        30,
                        "request-202"
                );

        when(
                jobRepository.findByIdempotencyKey(
                        "request-202"
                )
        ).thenReturn(
                Optional.empty(),
                Optional.of(existingJob)
        );

        when(jobApiMapper.toPayloadString(payload))
                .thenReturn(
                        """
                        {"message":"Hello"}
                        """
                );

        when(jobRepository.save(any(Job.class)))
                .thenReturn(false);

        Job result = service.submit(request);

        assertThat(result)
                .isSameAs(existingJob);

        verify(jobRepository).save(
                any(Job.class)
        );
    }

    @Test
    void shouldRejectNullRequest() {

        JobSubmissionService service =
                createService();

        assertThatThrownBy(
                () -> service.submit(null)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "request must not be null"
                );

        verify(jobRepository, never())
                .save(any(Job.class));
    }

    private JobSubmissionService createService() {

        return new JobSubmissionService(
                jobRepository,
                jobApiMapper,
                chronosQMetrics,
                new JobScheduleCalculator()
        );
    }

    private Job createExistingJob(
            String idempotencyKey
    ) {

        Instant now =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        return new Job(
                UUID.randomUUID(),
                "default",
                "PRINT_MESSAGE",
                """
                {"message":"Existing job"}
                """,
                JobStatus.READY,
                0,
                now,
                ScheduleType.IMMEDIATE,
                null,
                0,
                3,
                idempotencyKey,
                null,
                null,
                30,
                now,
                now,
                null,
                0L
        );
    }
}