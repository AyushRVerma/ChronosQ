package com.chronosq.metrics;

import java.util.Map;
import java.util.Objects;

import org.slf4j.MDC;

import com.chronosq.worker.ClaimedJob;

public final class JobLogContext implements AutoCloseable {

    private static final String JOB_ID = "jobId";
    private static final String EXECUTION_ID = "executionId";
    private static final String WORKER_ID = "workerId";
    private static final String JOB_TYPE = "jobType";
    private static final String QUEUE_NAME = "queueName";
    private static final String ATTEMPT_NUMBER = "attemptNumber";

    private final Map<String, String> previousContext;

    private JobLogContext(
            ClaimedJob claimedJob
    ) {
        Objects.requireNonNull(
                claimedJob,
                "claimedJob must not be null"
        );

        previousContext =
                MDC.getCopyOfContextMap();

        MDC.put(
                JOB_ID,
                claimedJob.job().id().toString()
        );

        MDC.put(
                EXECUTION_ID,
                claimedJob.execution().id().toString()
        );

        MDC.put(
                WORKER_ID,
                claimedJob.execution().workerId()
        );

        MDC.put(
                JOB_TYPE,
                claimedJob.job().jobType()
        );

        MDC.put(
                QUEUE_NAME,
                claimedJob.job().queueName()
        );

        MDC.put(
                ATTEMPT_NUMBER,
                String.valueOf(
                        claimedJob.execution().attemptNumber()
                )
        );
    }

    public static JobLogContext open(
            ClaimedJob claimedJob
    ) {
        return new JobLogContext(claimedJob);
    }

    @Override
    public void close() {
        MDC.clear();

        if (previousContext != null) {
            MDC.setContextMap(
                    previousContext
            );
        }
    }
}

//MDC (Mapped Diagnostic Context) is a feature built into Java logging frameworks (like SLF4J/Logback). It acts like a sticky note attached to the current thread.
//
//When this class calls MDC.put(JOB_ID, ...) and MDC.put(WORKER_ID, ...), it tells the logging system:

//If you put a sticky note on a thread, you must remember to take it off when you are done. If you forget to clear the MDC, the next job that uses that thread might accidentally print the previous job's ID!
//
//To prevent developers from forgetting to clear it, this class implements AutoCloseable.