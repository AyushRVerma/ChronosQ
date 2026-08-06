package com.chronosq.recovery;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.chronosq.handler.UnknownJobTypeException;
import com.chronosq.handler.WebhookDeliveryException;

@Component
public class RetryableFailureClassifier {

    public boolean isRetryable(
            Throwable failure
    ) {
        Objects.requireNonNull(
                failure,
                "failure must not be null"
        );

        if (failure instanceof WebhookDeliveryException exception) {
            return exception.isRetryable();
        }

        return !containsPermanentFailure(
                failure
        );
    }

    private boolean containsPermanentFailure(
            Throwable failure
    ) {
        Throwable currentFailure = failure;

        // Loop until we reach the end of the exception chain (where getCause() returns null)
        // ① IllegalArgumentException → programming‑error, never retry
        // ② UnknownJobTypeException   → we don’t know how to handle this job

        while (currentFailure != null) {
            if (currentFailure instanceof IllegalArgumentException
                    || currentFailure
                    instanceof UnknownJobTypeException) {

                return true;
            }

            currentFailure =
                    currentFailure.getCause();
        }

        return false;
    }
}