package com.chronosq.job.service;

import java.util.UUID;

public class JobAlreadyRequeuedException extends RuntimeException {
    public JobAlreadyRequeuedException(UUID jobId) {
        super("Dead-lettered job has already been requeued: " + jobId);
    }
}
