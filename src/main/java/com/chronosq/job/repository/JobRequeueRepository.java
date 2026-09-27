package com.chronosq.job.repository;

import com.chronosq.job.domain.JobRequeue;
import java.util.List;
import java.util.UUID;

public interface JobRequeueRepository {
    boolean existsForSource(UUID sourceJobId);
    void save(JobRequeue requeue);
    List<JobRequeue> findBySource(UUID sourceJobId);
}
