package com.chronosq.metrics;

public final class MetricNames {

    public static final String JOBS_SUBMITTED =
            "chronosq.jobs.submitted";

    public static final String JOBS_CLAIMED =
            "chronosq.jobs.claimed";

    public static final String EXECUTIONS_COMPLETED =
            "chronosq.executions.completed";

    public static final String EXECUTION_DURATION =
            "chronosq.execution.duration";

    public static final String RETRIES_SCHEDULED =
            "chronosq.jobs.retries.scheduled";

    public static final String JOBS_DEAD_LETTERED =
            "chronosq.jobs.dead_lettered";

    public static final String EXPIRED_LEASES_RECOVERED =
            "chronosq.jobs.leases.recovered";

    private MetricNames() {
    }
}