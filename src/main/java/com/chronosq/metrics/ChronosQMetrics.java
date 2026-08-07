package com.chronosq.metrics;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.chronosq.execution.ExecutionStatus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component

//This class handles Application Metrics for ChronosQ. It uses a popular Java library called Micrometer.
//ChronosQMetrics is the class responsible for updating the dashboard dials so that tools like
// Prometheus or Grafana can display beautiful charts showing the health of your job queue.
public class ChronosQMetrics {

    private final MeterRegistry meterRegistry;

    public ChronosQMetrics(
            MeterRegistry meterRegistry
    ) {
        this.meterRegistry = Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
        );
    }

    //Counting Things That Only Go Up
    public void incrementJobSubmitted() {
        Counter.builder("chronosq.jobs.submitted")
                .description("Number of submitted jobs")
                .register(meterRegistry)
                .increment();
    }

    public void incrementJobsClaimed(int claimedJobCount) {

        if (claimedJobCount <= 0) {
            return;
        }

        Counter.builder("chronosq.jobs.claimed")
                .description("Number of jobs claimed by workers")
                .register(meterRegistry)
                .increment(claimedJobCount);
    }

    //Measuring How Long Things Take
    public void recordExecution(
            ExecutionStatus status,
            long durationMs
    ) {
        Objects.requireNonNull(
                status,
                "status must not be null"
        );

        Counter.builder("chronosq.executions.completed")
                .description("Number of completed job executions")
                .tag("status", status.name())
                .register(meterRegistry)
                .increment();

        Timer.builder("chronosq.execution.duration")
                .description("Job execution duration")
                .tag("status", status.name())
                .register(meterRegistry)
                .record(
                        durationMs,
                        TimeUnit.MILLISECONDS
                );
    }

    public void incrementRetryScheduled() {
        Counter.builder("chronosq.jobs.retries.scheduled")
                .description("Number of jobs scheduled for retry")
                .register(meterRegistry)
                .increment();
    }

    public void incrementJobDeadLettered() {
        Counter.builder("chronosq.jobs.dead_lettered")
                .description("Number of dead-lettered jobs")
                .register(meterRegistry)
                .increment();
    }

    public void incrementExpiredLeaseRecovered() {
        Counter.builder("chronosq.jobs.leases.recovered")
                .description("Number of jobs recovered after lease expiration")
                .register(meterRegistry)
                .increment();
    }
}