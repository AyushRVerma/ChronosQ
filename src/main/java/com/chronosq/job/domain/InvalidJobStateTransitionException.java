package com.chronosq.job.domain;

public final class InvalidJobStateTransitionException
        extends IllegalStateException {

    private final JobStatus currentStatus;
    private final JobStatus requestedStatus;

    public InvalidJobStateTransitionException(
            JobStatus currentStatus,
            JobStatus requestedStatus
    ) {
        super(
                "Invalid job status transition: "
                        + currentStatus
                        + " -> "
                        + requestedStatus
        );

        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public JobStatus currentStatus() {
        return currentStatus;
    }

    public JobStatus requestedStatus() {
        return requestedStatus;
    }
}