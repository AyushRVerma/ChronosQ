package com.chronosq.handler;


//PrintMessagePayload represents the JSON data required by a PRINT_MESSAGE job.


//Instead of manually extracting "message" as raw text throughout your application,
//PrintMessagePayload parses and validates this JSON into a strongly-typed Java record!
public record PrintMessagePayload(String message) {

    private static final int MAX_MESSAGE_LENGTH = 10_000;

    public PrintMessagePayload {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Print message must not be blank"
            );
        }

        message = message.trim();

        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "Print message must not exceed "
                            + MAX_MESSAGE_LENGTH
                            + " characters"
            );
        }
    }
}