package com.chronosq.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronosq.configuration.WorkerProperties;
import com.chronosq.execution.JobExecutionRepository;
import com.chronosq.job.repository.JobRepository;
import com.chronosq.metrics.ChronosQMetrics;

@ExtendWith(MockitoExtension.class)
class JobClaimServiceCapacityTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Mock JobRepository jobRepository;
    @Mock JobExecutionRepository executionRepository;
    @Mock ChronosQMetrics metrics;

    @Test
    void doesNotClaimWhenNoExecutionCapacityRemains() {
        assertThat(service().claimAvailableJobs(NOW, 0)).isEmpty();
        verifyNoInteractions(jobRepository);
    }

    @Test
    void capsClaimBatchAtAvailableExecutionCapacity() {
        when(jobRepository.claimReadyJobs("default", "worker-1", NOW,
                NOW.plusSeconds(60), 2)).thenReturn(List.of());

        assertThat(service().claimAvailableJobs(NOW, 2)).isEmpty();

        verify(jobRepository).claimReadyJobs("default", "worker-1", NOW,
                NOW.plusSeconds(60), 2);
    }

    private JobClaimService service() {
        WorkerProperties properties = new WorkerProperties(
                true, "worker-1", "test-instance", "default", 10, 60);
        return new JobClaimService(
                jobRepository, metrics, executionRepository, properties);
    }
}
