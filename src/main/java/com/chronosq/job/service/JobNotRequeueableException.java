package com.chronosq.job.service;

import com.chronosq.job.domain.JobStatus;

public class JobNotRequeueableException extends RuntimeException {
    public JobNotRequeueableException(JobStatus status) {
        super("Only DEAD_LETTERED jobs can be requeued; current status is " + status);
    }
}
