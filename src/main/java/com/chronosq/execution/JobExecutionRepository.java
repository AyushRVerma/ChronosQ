package com.chronosq.execution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobExecutionRepository {

    void save(JobExecution execution);

    Optional<JobExecution> findById(UUID executionId);

    List<JobExecution> findByJobId(UUID jobId);

    boolean finalizeExecution(
            UUID executionId,
            String workerId,
            ExecutionResult result
    );

}