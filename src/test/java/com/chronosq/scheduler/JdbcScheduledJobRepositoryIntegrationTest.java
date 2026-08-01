package com.chronosq.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context
        .SpringBootTest;

import org.springframework.boot.testcontainers
        .service.connection.ServiceConnection;

import org.springframework.jdbc.core.simple.JdbcClient;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.testcontainers.postgresql
        .PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
        properties = {
                "chronosq.scheduler.enabled=false"
        }
)
class JdbcScheduledJobRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgresContainer =
            new PostgreSQLContainer(
                    "postgres:17-alpine"
            );

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
    void shouldPromoteDueScheduledJob() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job dueJob = createJob(
                JobStatus.SCHEDULED,
                Instant.parse(
                        "2026-01-01T09:55:00Z"
                ),
                5,
                Instant.parse(
                        "2026-01-01T09:00:00Z"
                )
        );

        assertThat(
                jobRepository.save(dueJob)
        ).isTrue();

        int promotedCount =
                jobRepository.promoteDueJobs(
                        currentTime,
                        100
                );

        Job updatedJob =
                jobRepository
                        .findById(dueJob.id())
                        .orElseThrow();

        assertThat(promotedCount)
                .isEqualTo(1);

        assertThat(updatedJob.status())
                .isEqualTo(JobStatus.READY);

        assertThat(updatedJob.updatedAt())
                .isEqualTo(currentTime);

        assertThat(updatedJob.version())
                .isEqualTo(1L);
    }

    @Test
    void shouldNotPromoteFutureJob() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job futureJob = createJob(
                JobStatus.SCHEDULED,
                Instant.parse(
                        "2026-01-01T11:00:00Z"
                ),
                5,
                Instant.parse(
                        "2026-01-01T09:00:00Z"
                )
        );

        jobRepository.save(futureJob);

        int promotedCount =
                jobRepository.promoteDueJobs(
                        currentTime,
                        100
                );

        Job unchangedJob =
                jobRepository
                        .findById(futureJob.id())
                        .orElseThrow();

        assertThat(promotedCount)
                .isZero();

        assertThat(unchangedJob.status())
                .isEqualTo(
                        JobStatus.SCHEDULED
                );

        assertThat(unchangedJob.version())
                .isZero();
    }

    @Test
    void shouldNotChangeJobThatIsAlreadyReady() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job readyJob = createJob(
                JobStatus.READY,
                Instant.parse(
                        "2026-01-01T09:00:00Z"
                ),
                10,
                Instant.parse(
                        "2026-01-01T08:00:00Z"
                )
        );

        jobRepository.save(readyJob);

        int promotedCount =
                jobRepository.promoteDueJobs(
                        currentTime,
                        100
                );

        Job unchangedJob =
                jobRepository
                        .findById(readyJob.id())
                        .orElseThrow();

        assertThat(promotedCount)
                .isZero();

        assertThat(unchangedJob.status())
                .isEqualTo(JobStatus.READY);

        assertThat(unchangedJob.version())
                .isZero();
    }

    @Test
    void shouldRespectBatchSize() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job oldestJob = createJob(
                JobStatus.SCHEDULED,
                Instant.parse(
                        "2026-01-01T09:00:00Z"
                ),
                5,
                Instant.parse(
                        "2026-01-01T08:00:00Z"
                )
        );

        Job middleJob = createJob(
                JobStatus.SCHEDULED,
                Instant.parse(
                        "2026-01-01T09:05:00Z"
                ),
                5,
                Instant.parse(
                        "2026-01-01T08:01:00Z"
                )
        );

        Job newestJob = createJob(
                JobStatus.SCHEDULED,
                Instant.parse(
                        "2026-01-01T09:10:00Z"
                ),
                5,
                Instant.parse(
                        "2026-01-01T08:02:00Z"
                )
        );

        jobRepository.save(newestJob);
        jobRepository.save(middleJob);
        jobRepository.save(oldestJob);

        int promotedCount =
                jobRepository.promoteDueJobs(
                        currentTime,
                        2
                );

        assertThat(promotedCount)
                .isEqualTo(2);

        assertThat(findJob(oldestJob.id()).status())
                .isEqualTo(JobStatus.READY);

        assertThat(findJob(middleJob.id()).status())
                .isEqualTo(JobStatus.READY);

        assertThat(findJob(newestJob.id()).status())
                .isEqualTo(
                        JobStatus.SCHEDULED
                );
    }

    @Test
    void shouldUsePriorityWhenAvailableTimesMatch() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Instant availableAt =
                Instant.parse(
                        "2026-01-01T09:00:00Z"
                );

        Job lowPriorityJob = createJob(
                JobStatus.SCHEDULED,
                availableAt,
                1,
                Instant.parse(
                        "2026-01-01T08:00:00Z"
                )
        );

        Job highPriorityJob = createJob(
                JobStatus.SCHEDULED,
                availableAt,
                20,
                Instant.parse(
                        "2026-01-01T08:01:00Z"
                )
        );

        jobRepository.save(lowPriorityJob);
        jobRepository.save(highPriorityJob);

        int promotedCount =
                jobRepository.promoteDueJobs(
                        currentTime,
                        1
                );

        assertThat(promotedCount)
                .isEqualTo(1);

        assertThat(
                findJob(highPriorityJob.id()).status()
        ).isEqualTo(JobStatus.READY);

        assertThat(
                findJob(lowPriorityJob.id()).status()
        ).isEqualTo(JobStatus.SCHEDULED);
    }

    @Test
    void shouldPromoteRemainingJobsInNextBatch() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job firstJob = createJob(
                JobStatus.SCHEDULED,
                Instant.parse(
                        "2026-01-01T09:00:00Z"
                ),
                5,
                Instant.parse(
                        "2026-01-01T08:00:00Z"
                )
        );

        Job secondJob = createJob(
                JobStatus.SCHEDULED,
                Instant.parse(
                        "2026-01-01T09:01:00Z"
                ),
                5,
                Instant.parse(
                        "2026-01-01T08:01:00Z"
                )
        );

        jobRepository.save(firstJob);
        jobRepository.save(secondJob);

        int firstCycle =
                jobRepository.promoteDueJobs(
                        currentTime,
                        1
                );

        int secondCycle =
                jobRepository.promoteDueJobs(
                        currentTime,
                        1
                );

        int thirdCycle =
                jobRepository.promoteDueJobs(
                        currentTime,
                        1
                );

        assertThat(firstCycle)
                .isEqualTo(1);

        assertThat(secondCycle)
                .isEqualTo(1);

        assertThat(thirdCycle)
                .isZero();

        assertThat(findJob(firstJob.id()).status())
                .isEqualTo(JobStatus.READY);

        assertThat(findJob(secondJob.id()).status())
                .isEqualTo(JobStatus.READY);
    }

    @Test
    void shouldRejectInvalidBatchSize() {

        assertThatThrownBy(
                () -> jobRepository.promoteDueJobs(
                        Instant.parse(
                                "2026-01-01T10:00:00Z"
                        ),
                        0
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "batchSize must be at least 1"
                );
    }

    @Test
    void shouldRejectNullCurrentTime() {

        assertThatThrownBy(
                () -> jobRepository.promoteDueJobs(
                        null,
                        100
                )
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "currentTime must not be null"
                );
    }

    private Job findJob(UUID jobId) {

        return jobRepository
                .findById(jobId)
                .orElseThrow();
    }

    private Job createJob(
            JobStatus status,
            Instant availableAt,
            int priority,
            Instant createdAt
    ) {

        return new Job(
                UUID.randomUUID(),
                "default",
                "PRINT_MESSAGE",
                """
                {
                  "message": "Scheduled message"
                }
                """,
                status,
                priority,
                availableAt,
                ScheduleType.ONE_TIME,
                null,
                0,
                3,
                null,
                null,
                null,
                30,
                createdAt,
                createdAt,
                null,
                0L
        );
    }
}