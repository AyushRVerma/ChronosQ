package com.chronosq.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

//import com.chronosq.job.domain.Job;
//import com.chronosq.job.domain.JobStatus;
//import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class JdbcJobExecutionRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgresContainer =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JobExecutionRepository executionRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {

        jdbcClient.sql("""
                DELETE FROM job_executions
                """)
                .update();

        jdbcClient.sql("""
                DELETE FROM jobs
                """)
                .update();
    }

    @Test
    void shouldSaveAndFindRunningExecution() {

        Job job = createJob();
        jobRepository.save(job);

        JobExecution execution = new JobExecution(
                UUID.randomUUID(),
                job.id(),
                "worker-1",
                1,
                ExecutionStatus.RUNNING,
                Instant.parse("2026-01-01T10:00:00Z"),
                null,
                null,
                null,
                null
        );

        executionRepository.save(execution);

        JobExecution savedExecution =
                executionRepository.findById(execution.id())
                        .orElseThrow();

        assertThat(savedExecution)
                .isEqualTo(execution);
    }

    @Test
    void shouldReturnExecutionsOrderedByAttemptNumber() {

        Job job = createJob();
        jobRepository.save(job);

        JobExecution firstAttempt = new JobExecution(
                UUID.randomUUID(),
                job.id(),
                "worker-1",
                1,
                ExecutionStatus.FAILED,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:02Z"),
                2_000L,
                "NETWORK_ERROR",
                "Remote service was unavailable"
        );

        JobExecution secondAttempt = new JobExecution(
                UUID.randomUUID(),
                job.id(),
                "worker-2",
                2,
                ExecutionStatus.SUCCEEDED,
                Instant.parse("2026-01-01T10:01:00Z"),
                Instant.parse("2026-01-01T10:01:01Z"),
                1_000L,
                null,
                null
        );

        // Intentionally saved in the opposite order.
        executionRepository.save(secondAttempt);
        executionRepository.save(firstAttempt);

        List<JobExecution> executions =
                executionRepository.findByJobId(job.id());

        assertThat(executions)
                .containsExactly(
                        firstAttempt,
                        secondAttempt
                );
    }

    @Test
    void shouldReturnEmptyWhenExecutionDoesNotExist() {

        assertThat(
                executionRepository.findById(UUID.randomUUID())
        ).isEmpty();
    }

    private Job createJob() {

        Instant now = Instant.parse(
                "2026-01-01T09:55:00Z"
        );

        return new Job(
                UUID.randomUUID(),
                "default",
                "SEND_EMAIL",
                """
                {
                  "email": "learner@example.com"
                }
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
    @Test
    void shouldUpdateJobStatusAndVersion() {

        Job job = createImmediateJob();
        jobRepository.save(job);

        Instant updateTime =
                Instant.parse("2026-01-01T10:05:00Z");

        boolean updated = jobRepository.updateStatus(
                job.id(),
                JobStatus.READY,
                JobStatus.RUNNING,
                updateTime,
                null,
                0L
        );

        Job updatedJob = jobRepository
                .findById(job.id())
                .orElseThrow();

        assertThat(updated).isTrue();

        assertThat(updatedJob.status())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(updatedJob.updatedAt())
                .isEqualTo(updateTime);

        assertThat(updatedJob.version())
                .isEqualTo(1L);
    }

    @Test
    void shouldNotUpdateWhenVersionIsIncorrect() {

        Job job = createImmediateJob();
        jobRepository.save(job);

        boolean updated = jobRepository.updateStatus(
                job.id(),
                JobStatus.READY,
                JobStatus.RUNNING,
                Instant.parse("2026-01-01T10:05:00Z"),
                null,
                99L
        );

        Job unchangedJob = jobRepository
                .findById(job.id())
                .orElseThrow();

        assertThat(updated).isFalse();

        assertThat(unchangedJob.status())
                .isEqualTo(JobStatus.READY);

        assertThat(unchangedJob.version())
                .isEqualTo(0L);
    }

    @Test
    void shouldNotUpdateWhenCurrentStatusDoesNotMatch() {

        Job job = createImmediateJob();
        jobRepository.save(job);

        boolean updated = jobRepository.updateStatus(
                job.id(),
                JobStatus.RUNNING,
                JobStatus.SUCCEEDED,
                Instant.parse("2026-01-01T10:05:00Z"),
                Instant.parse("2026-01-01T10:05:00Z"),
                0L
        );

        Job unchangedJob = jobRepository
                .findById(job.id())
                .orElseThrow();

        assertThat(updated).isFalse();

        assertThat(unchangedJob.status())
                .isEqualTo(JobStatus.READY);
    }

    private Job createImmediateJob() {

        Instant now =
                Instant.parse("2026-01-01T10:00:00Z");

        return new Job(
                UUID.randomUUID(),
                "default",
                "SEND_EMAIL",
                """
                {
                  "email": "learner@example.com"
                }
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
}