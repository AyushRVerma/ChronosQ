package com.chronosq.job.service;

import java.util.UUID;

public class ConcurrentJobModificationException
        extends RuntimeException {

    private final UUID jobId;

    public ConcurrentJobModificationException(
            UUID jobId
    ) {
        super(
                "Job was changed by another process: "
                        + jobId
        );

        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}