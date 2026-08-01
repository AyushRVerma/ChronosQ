package com.chronosq.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.chronosq.execution.ExecutionStatus;
import com.chronosq.execution.JobExecution;
import com.chronosq.execution.JobExecutionRepository;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobQueryServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository
            jobExecutionRepository;

    @Test
    void shouldReturnExistingJob() {

        JobQueryService service =
                createService();

        Job job = createJob();

        when(jobRepository.findById(job.id()))
                .thenReturn(Optional.of(job));

        Job result = service.getJob(job.id());

        assertThat(result)
                .isSameAs(job);
    }

    @Test
    void shouldThrowWhenJobDoesNotExist() {

        JobQueryService service =
                createService();

        UUID jobId = UUID.randomUUID();

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.getJob(jobId)
        )
                .isInstanceOf(
                        JobNotFoundException.class
                )
                .hasMessageContaining(
                        jobId.toString()
                );
    }

    @Test
    void shouldReturnJobExecutionHistory() {

        JobQueryService service =
                createService();

        Job job = createJob();

        JobExecution firstAttempt =
                createFailedExecution(
                        job.id(),
                        1
                );

        JobExecution secondAttempt =
                createSuccessfulExecution(
                        job.id(),
                        2
                );

        List<JobExecution> executions =
                List.of(
                        firstAttempt,
                        secondAttempt
                );

        when(jobRepository.findById(job.id()))
                .thenReturn(Optional.of(job));

        when(
                jobExecutionRepository.findByJobId(
                        job.id()
                )
        ).thenReturn(executions);

        List<JobExecution> result =
                service.getExecutions(job.id());

        assertThat(result)
                .containsExactly(
                        firstAttempt,
                        secondAttempt
                );
    }

    @Test
    void shouldReturnEmptyExecutionHistory() {

        JobQueryService service =
                createService();

        Job job = createJob();

        when(jobRepository.findById(job.id()))
                .thenReturn(Optional.of(job));

        when(
                jobExecutionRepository.findByJobId(
                        job.id()
                )
        ).thenReturn(List.of());

        List<JobExecution> result =
                service.getExecutions(job.id());

        assertThat(result)
                .isEmpty();
    }

    @Test
    void shouldNotQueryExecutionsWhenJobIsMissing() {

        JobQueryService service =
                createService();

        UUID jobId = UUID.randomUUID();

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.getExecutions(jobId)
        )
                .isInstanceOf(
                        JobNotFoundException.class
                );

        verify(
                jobExecutionRepository,
                never()
        ).findByJobId(jobId);
    }

    @Test
    void shouldRejectNullJobIdForGetJob() {

        JobQueryService service =
                createService();

        assertThatThrownBy(
                () -> service.getJob(null)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "jobId must not be null"
                );
    }

    @Test
    void shouldRejectNullJobIdForExecutions() {

        JobQueryService service =
                createService();

        assertThatThrownBy(
                () -> service.getExecutions(null)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "jobId must not be null"
                );
    }

    private JobQueryService createService() {

        return new JobQueryService(
                jobRepository,
                jobExecutionRepository
        );
    }

    private Job createJob() {

        Instant now =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        return new Job(
                UUID.randomUUID(),
                "default",
                "PRINT_MESSAGE",
                """
                {"message":"Hello"}
                """,
                JobStatus.READY,
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
                null,
                0L
        );
    }

    private JobExecution createFailedExecution(
            UUID jobId,
            int attemptNumber
    ) {

        return new JobExecution(
                UUID.randomUUID(),
                jobId,
                "worker-1",
                attemptNumber,
                ExecutionStatus.FAILED,
                Instant.parse(
                        "2026-01-01T10:01:00Z"
                ),
                Instant.parse(
                        "2026-01-01T10:01:02Z"
                ),
                2_000L,
                "NETWORK_ERROR",
                "Remote service was unavailable"
        );
    }

    private JobExecution createSuccessfulExecution(
            UUID jobId,
            int attemptNumber
    ) {

        return new JobExecution(
                UUID.randomUUID(),
                jobId,
                "worker-2",
                attemptNumber,
                ExecutionStatus.SUCCEEDED,
                Instant.parse(
                        "2026-01-01T10:02:00Z"
                ),
                Instant.parse(
                        "2026-01-01T10:02:01Z"
                ),
                1_000L,
                null,
                null
        );
    }
}