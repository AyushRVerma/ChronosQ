package com.chronosq.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.chronosq.execution.JobExecution;
import com.chronosq.execution
        .JobExecutionRepository;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;
import com.chronosq.job.repository.JobRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation
        .Autowired;

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
                "chronosq.scheduler.enabled=false",
                "chronosq.worker.enabled=true",
                "chronosq.worker.worker-id=worker-test",
                "chronosq.worker.instance-name=test-instance",
                "chronosq.worker.queue-name=default",
                "chronosq.worker.claim-batch-size=10",
                "chronosq.worker.lease-duration-seconds=60"
        }
)
class JdbcJobClaimIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgresContainer =
            new PostgreSQLContainer(
                    "postgres:17-alpine"
            );

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobExecutionRepository
            jobExecutionRepository;

    @Autowired
    private JobClaimService jobClaimService;

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
    void shouldAtomicallyClaimReadyJob() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job readyJob = createReadyJob(
                "default",
                10,
                currentTime.minusSeconds(60),
                currentTime.minusSeconds(120)
        );

        jobRepository.save(readyJob);

        List<Job> claimedJobs =
                jobRepository.claimReadyJobs(
                        "default",
                        "worker-1",
                        currentTime,
                        currentTime.plusSeconds(60),
                        10
                );

        assertThat(claimedJobs)
                .hasSize(1);

        Job claimedJob = claimedJobs.get(0);

        assertThat(claimedJob.id())
                .isEqualTo(readyJob.id());

        assertThat(claimedJob.status())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(claimedJob.lockedBy())
                .isEqualTo("worker-1");

        assertThat(claimedJob.leaseExpiresAt())
                .isEqualTo(
                        currentTime.plusSeconds(60)
                );

        assertThat(claimedJob.attemptCount())
                .isEqualTo(1);

        assertThat(claimedJob.version())
                .isEqualTo(1L);

        Job storedJob =
                jobRepository
                        .findById(readyJob.id())
                        .orElseThrow();

        assertThat(storedJob)
                .isEqualTo(claimedJob);
    }

    @Test
    void shouldClaimHighestPriorityFirst() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job lowPriority = createReadyJob(
                "default",
                1,
                currentTime,
                currentTime.minusSeconds(120)
        );

        Job highPriority = createReadyJob(
                "default",
                20,
                currentTime,
                currentTime.minusSeconds(60)
        );

        jobRepository.save(lowPriority);
        jobRepository.save(highPriority);

        List<Job> claimedJobs =
                jobRepository.claimReadyJobs(
                        "default",
                        "worker-1",
                        currentTime,
                        currentTime.plusSeconds(60),
                        1
                );

        assertThat(claimedJobs)
                .hasSize(1);

        assertThat(claimedJobs.get(0).id())
                .isEqualTo(highPriority.id());

        assertThat(
                findJob(lowPriority.id()).status()
        ).isEqualTo(JobStatus.READY);
    }

    @Test
    void shouldUseOldestJobWhenPrioritiesMatch() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job olderJob = createReadyJob(
                "default",
                10,
                currentTime,
                currentTime.minusSeconds(120)
        );

        Job newerJob = createReadyJob(
                "default",
                10,
                currentTime,
                currentTime.minusSeconds(60)
        );

        jobRepository.save(newerJob);
        jobRepository.save(olderJob);

        List<Job> claimedJobs =
                jobRepository.claimReadyJobs(
                        "default",
                        "worker-1",
                        currentTime,
                        currentTime.plusSeconds(60),
                        1
                );

        assertThat(claimedJobs)
                .hasSize(1);

        assertThat(claimedJobs.get(0).id())
                .isEqualTo(olderJob.id());
    }

    @Test
    void shouldOnlyClaimConfiguredQueue() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job defaultJob = createReadyJob(
                "default",
                10,
                currentTime,
                currentTime
        );

        Job emailJob = createReadyJob(
                "emails",
                20,
                currentTime,
                currentTime
        );

        jobRepository.save(defaultJob);
        jobRepository.save(emailJob);

        List<Job> claimedJobs =
                jobRepository.claimReadyJobs(
                        "default",
                        "worker-1",
                        currentTime,
                        currentTime.plusSeconds(60),
                        10
                );

        assertThat(claimedJobs)
                .extracting(Job::id)
                .containsExactly(defaultJob.id());

        assertThat(findJob(emailJob.id()).status())
                .isEqualTo(JobStatus.READY);
    }

    @Test
    void shouldNotClaimFutureReadyJob() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job futureJob = createReadyJob(
                "default",
                10,
                currentTime.plusSeconds(600),
                currentTime
        );

        jobRepository.save(futureJob);

        List<Job> claimedJobs =
                jobRepository.claimReadyJobs(
                        "default",
                        "worker-1",
                        currentTime,
                        currentTime.plusSeconds(60),
                        10
                );

        assertThat(claimedJobs)
                .isEmpty();

        assertThat(findJob(futureJob.id()).status())
                .isEqualTo(JobStatus.READY);
    }

    @Test
    void shouldCreateExecutionWhenServiceClaimsJob() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job readyJob = createReadyJob(
                "default",
                10,
                currentTime,
                currentTime.minusSeconds(60)
        );

        jobRepository.save(readyJob);

        List<ClaimedJob> results =
                jobClaimService
                        .claimAvailableJobs(
                                currentTime
                        );

        assertThat(results)
                .hasSize(1);

        ClaimedJob result = results.get(0);

        assertThat(result.job().status())
                .isEqualTo(JobStatus.RUNNING);

        assertThat(result.job().lockedBy())
                .isEqualTo("worker-test");

        assertThat(result.job().attemptCount())
                .isEqualTo(1);

        JobExecution execution =
                result.execution();

        assertThat(execution.jobId())
                .isEqualTo(readyJob.id());

        assertThat(execution.workerId())
                .isEqualTo("worker-test");

        assertThat(execution.attemptNumber())
                .isEqualTo(1);

        assertThat(execution.startedAt())
                .isEqualTo(currentTime);

        List<JobExecution> storedExecutions =
                jobExecutionRepository
                        .findByJobId(
                                readyJob.id()
                        );

        assertThat(storedExecutions)
                .containsExactly(execution);
    }

    @Test
    void shouldNotClaimSameJobTwice() {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        Job readyJob = createReadyJob(
                "default",
                10,
                currentTime,
                currentTime
        );

        jobRepository.save(readyJob);

        List<Job> firstClaim =
                jobRepository.claimReadyJobs(
                        "default",
                        "worker-1",
                        currentTime,
                        currentTime.plusSeconds(60),
                        10
                );

        List<Job> secondClaim =
                jobRepository.claimReadyJobs(
                        "default",
                        "worker-2",
                        currentTime,
                        currentTime.plusSeconds(60),
                        10
                );

        assertThat(firstClaim)
                .hasSize(1);

        assertThat(secondClaim)
                .isEmpty();

        assertThat(findJob(readyJob.id()).lockedBy())
                .isEqualTo("worker-1");
    }

    @Test
    void shouldAllowConcurrentWorkersWithoutDuplicates()
            throws Exception {

        Instant currentTime =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        int totalJobs = 20;

        for (int index = 0;
             index < totalJobs;
             index++) {

            Job job = createReadyJob(
                    "default",
                    index,
                    currentTime,
                    currentTime.minusSeconds(index)
            );

            jobRepository.save(job);
        }

        ExecutorService executorService =
                Executors.newFixedThreadPool(2);

        CountDownLatch startSignal =
                new CountDownLatch(1);

        try {
            Future<List<Job>> workerA =
                    executorService.submit(
                            () -> {
                                startSignal.await();

                                return jobRepository
                                        .claimReadyJobs(
                                                "default",
                                                "worker-a",
                                                currentTime,
                                                currentTime
                                                        .plusSeconds(60),
                                                totalJobs
                                        );
                            }
                    );

            Future<List<Job>> workerB =
                    executorService.submit(
                            () -> {
                                startSignal.await();

                                return jobRepository
                                        .claimReadyJobs(
                                                "default",
                                                "worker-b",
                                                currentTime,
                                                currentTime
                                                        .plusSeconds(60),
                                                totalJobs
                                        );
                            }
                    );

            startSignal.countDown();

            List<Job> workerAJobs =
                    workerA.get(
                            10,
                            TimeUnit.SECONDS
                    );

            List<Job> workerBJobs =
                    workerB.get(
                            10,
                            TimeUnit.SECONDS
                    );

            List<Job> allClaimedJobs =
                    new ArrayList<>();

            allClaimedJobs.addAll(workerAJobs);
            allClaimedJobs.addAll(workerBJobs);

            Set<UUID> uniqueJobIds =
                    new HashSet<>();

            for (Job job : allClaimedJobs) {
                uniqueJobIds.add(job.id());
            }

            assertThat(allClaimedJobs)
                    .hasSize(totalJobs);

            assertThat(uniqueJobIds)
                    .hasSize(totalJobs);

            assertThat(allClaimedJobs)
                    .allMatch(
                            job -> job.status()
                                    == JobStatus.RUNNING
                    );

            assertThat(allClaimedJobs)
                    .allMatch(
                            job -> job.lockedBy()
                                    .equals("worker-a")
                                    || job.lockedBy()
                                    .equals("worker-b")
                    );
        } finally {
            executorService.shutdownNow();
        }
    }

    private Job findJob(UUID jobId) {

        return jobRepository
                .findById(jobId)
                .orElseThrow();
    }

    private Job createReadyJob(
            String queueName,
            int priority,
            Instant availableAt,
            Instant createdAt
    ) {

        return new Job(
                UUID.randomUUID(),
                queueName,
                "PRINT_MESSAGE",
                """
                {
                  "message": "Hello from ChronosQ"
                }
                """,
                JobStatus.READY,
                priority,
                availableAt,
                ScheduleType.IMMEDIATE,
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