package com.chronosq.job.service;

import java.util.UUID;

public class JobNotFoundException
        extends RuntimeException {

    private final UUID jobId;

    public JobNotFoundException(UUID jobId) {
        super("Job was not found: " + jobId);
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}