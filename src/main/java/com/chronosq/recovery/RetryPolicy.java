package com.chronosq.recovery;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import com.chronosq.configuration.RetryProperties;
import com.chronosq.job.domain.Job;

//RetryPolicy answers two critical questions when a job execution fails:
//canRetry(job): "Is this job allowed to be retried?"
//calculateNextRetryAt(job, failedAt): "EXACTLY when in the future should the next retry run?"
@Component
public class RetryPolicy {

    private final RetryProperties properties;

    public RetryPolicy(
            RetryProperties properties
    ) {
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );
    }

    public boolean canRetry(Job job) {

        Objects.requireNonNull(job, "job must not be null");

        return properties.enabled()
                && job.attemptCount() < job.maxAttempts();
    }

    public Instant calculateNextRetryAt(Job job, Instant failedAt) {

        Objects.requireNonNull(job, "job must not be null");

        Objects.requireNonNull(failedAt, "failedAt must not be null");

        long baseDelayMs = calculateBaseDelayMs(job.attemptCount());

        long jitteredDelayMs = applyJitter(baseDelayMs);

        return failedAt.plusMillis(jitteredDelayMs);
    }

    private long calculateBaseDelayMs(int attemptNumber) {
        int exponent = Math.max(0, attemptNumber - 1);

        double calculatedDelay = properties.initialDelayMs()
                        * Math.pow(properties.multiplier(), exponent);

        long roundedDelay = Math.round(calculatedDelay);

        return Math.min(
                roundedDelay,
                properties.maximumDelayMs()
        );
    }

    private long applyJitter(long baseDelayMs) {

        long jitterRangeMs = Math.round(baseDelayMs
                        * properties.jitterFactor()
        );

        long minimumDelayMs = Math.max(0,
                baseDelayMs - jitterRangeMs
        );

        long maximumDelayMs = Math.min(
                properties.maximumDelayMs(),
                baseDelayMs + jitterRangeMs
        );

        if (minimumDelayMs == maximumDelayMs) {
            return minimumDelayMs;
        }

        //In multithreaded Java applications (like our worker engine running 10 threads concurrently):
        //
        //Using standard new java.util.Random() causes thread contention because all threads compete for the same random seed state.
        //ThreadLocalRandom.current() gives each thread its own independent random generator with zero thread contention and maximum speed!
        return ThreadLocalRandom.current().nextLong(
                minimumDelayMs,
                maximumDelayMs + 1
        );
    }
}