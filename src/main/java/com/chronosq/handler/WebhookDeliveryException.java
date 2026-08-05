package com.chronosq.handler;


//This custom exception represents an HTTP delivery failure when sending a webhook to a remote server.
public class WebhookDeliveryException extends RuntimeException {

    private final int statusCode;

    public WebhookDeliveryException(int statusCode) {

        super("Webhook delivery failed with HTTP status "
                        + statusCode);

        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    //In network engineering, some HTTP errors are temporary (transient) and worth retrying, while others are permanent (fatal) and should never be retried.
    public boolean isRetryable() {
        return statusCode == 408
                || statusCode == 425
                || statusCode == 429
                || statusCode >= 500;
    }
}