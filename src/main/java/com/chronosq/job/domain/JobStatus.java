package com.chronosq.job.domain;

public enum JobStatus {

    SCHEDULED,

    READY,

    RUNNING,

    SUCCEEDED,

    RETRY_WAIT,

    DEAD_LETTERED,

    CANCELLED

}
