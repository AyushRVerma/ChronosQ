package com.chronosq.recovery;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.chronosq.job.domain.Job;

@Service
public class RetryDecisionService {

    private final RetryPolicy retryPolicy;
    private final RetryableFailureClassifier failureClassifier;

    public RetryDecisionService(
            RetryPolicy retryPolicy,
            RetryableFailureClassifier failureClassifier
    ) {
        this.retryPolicy = Objects.requireNonNull(
                retryPolicy,
                "retryPolicy must not be null"
        );

        this.failureClassifier = Objects.requireNonNull(
                failureClassifier,
                "failureClassifier must not be null"
        );
    }

    public RetryDecision decide(
            Job job,
            Throwable failure,
            Instant failedAt
    ) {
        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        Objects.requireNonNull(
                failure,
                "failure must not be null"
        );

        Objects.requireNonNull(
                failedAt,
                "failedAt must not be null"
        );

        boolean retryableFailure =
                failureClassifier.isRetryable(
                        failure
                );

        if (!retryableFailure
                || !retryPolicy.canRetry(job)) {

            return RetryDecision.deadLetter();
        }

        Instant nextRetryAt =
                retryPolicy.calculateNextRetryAt(
                        job,
                        failedAt
                );

        return RetryDecision.retryAt(
                nextRetryAt
        );
    }
}