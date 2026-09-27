package com.chronosq.job.domain;

public enum MissedExecutionPolicy {
    SKIP,
    RUN_ONCE_IMMEDIATELY,
    CATCH_UP_ALL
}
