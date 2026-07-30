package com.chronosq.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
class JdbcJobRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    "postgres:17-alpine"
            );

    private static final Instant NOW =
            Instant.parse("2026-07-30T10:00:00Z");

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void deleteExistingJobs() {
        jdbcClient
                .sql("DELETE FROM job_executions")
                .update();

        jdbcClient
                .sql("DELETE FROM jobs")
                .update();
    }

    @Test
    void shouldSaveAndFindJobById() {
        UUID jobId = UUID.randomUUID();

        Job job = createImmediateJob(
                jobId,
                "save-and-find-job-001"
        );

        jobRepository.save(job);

        Optional<Job> result =
                jobRepository.findById(jobId);

        assertThat(result).isPresent();

        Job savedJob = result.orElseThrow();

        assertThat(savedJob.id()).isEqualTo(jobId);
        assertThat(savedJob.queueName()).isEqualTo("demo");

        assertThat(savedJob.jobType())
                .isEqualTo("PRINT_MESSAGE");

        assertThat(savedJob.status())
                .isEqualTo(JobStatus.READY);

        assertThat(savedJob.scheduleType())
                .isEqualTo(ScheduleType.IMMEDIATE);

        assertThat(savedJob.priority()).isEqualTo(5);
        assertThat(savedJob.attemptCount()).isZero();
        assertThat(savedJob.maxAttempts()).isEqualTo(3);
        assertThat(savedJob.timeoutSeconds()).isEqualTo(60);
        assertThat(savedJob.availableAt()).isEqualTo(NOW);
        assertThat(savedJob.createdAt()).isEqualTo(NOW);
        assertThat(savedJob.updatedAt()).isEqualTo(NOW);
        assertThat(savedJob.version()).isZero();

        assertThat(savedJob.payload())
                .contains("\"message\"")
                .contains("Hello from ChronosQ");
    }

    @Test
    void shouldFindJobByIdempotencyKey() {
        Job job = createImmediateJob(
                UUID.randomUUID(),
                "idempotency-key-001"
        );

        jobRepository.save(job);

        Optional<Job> result =
                jobRepository.findByIdempotencyKey(
                        "idempotency-key-001"
                );

        assertThat(result).isPresent();

        assertThat(result.orElseThrow().id())
                .isEqualTo(job.id());
    }

    @Test
    void shouldReturnEmptyWhenJobIdDoesNotExist() {
        Optional<Job> result =
                jobRepository.findById(
                        UUID.randomUUID()
                );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenIdempotencyKeyDoesNotExist() {
        Optional<Job> result =
                jobRepository.findByIdempotencyKey(
                        "missing-key"
                );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSaveFixedIntervalJob() {
        UUID jobId = UUID.randomUUID();

        Job job = new Job(
                jobId,
                "reports",
                "GENERATE_REPORT",
                """
                {
                  "reportName": "Hourly report",
                  "format": "CSV"
                }
                """,
                JobStatus.SCHEDULED,
                2,
                NOW.plusSeconds(3600),
                ScheduleType.FIXED_INTERVAL,
                3600L,
                0,
                3,
                "fixed-report-001",
                null,
                null,
                120,
                NOW,
                NOW,
                null,
                0
        );

        jobRepository.save(job);

        Job savedJob = jobRepository
                .findById(jobId)
                .orElseThrow();

        assertThat(savedJob.scheduleType())
                .isEqualTo(ScheduleType.FIXED_INTERVAL);

        assertThat(savedJob.intervalSeconds())
                .isEqualTo(3600L);

        assertThat(savedJob.status())
                .isEqualTo(JobStatus.SCHEDULED);
    }

    public static Job createImmediateJob(
            UUID jobId,
            String idempotencyKey
    ) {
        return new Job(
                jobId,
                "demo",
                "PRINT_MESSAGE",
                """
                {
                  "message": "Hello from ChronosQ"
                }
                """,
                JobStatus.READY,
                5,
                NOW,
                ScheduleType.IMMEDIATE,
                null,
                0,
                3,
                idempotencyKey,
                null,
                null,
                60,
                NOW,
                NOW,
                null,
                0
        );
    }
}