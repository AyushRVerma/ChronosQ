package com.chronosq.execution;

import java.util.UUID;

public final class JobExecutionTimeoutException
        extends RuntimeException {

    public JobExecutionTimeoutException(
            UUID jobId,
            int timeoutSeconds
    ) {
        super(
                "Job %s exceeded its %d second execution timeout"
                        .formatted(jobId, timeoutSeconds)
        );
    }
}
