package com.chronosq.api;

import java.time.Instant;
import java.util.Map;

// Global Error Handling DTO
//When an exception or error occurs in your application (e.g. Job Not Found, Validation Failure,
// Illegal State Transition), instead of returning a messy HTML error page or raw stack trace,
// Spring uses ApiErrorResponse to return a standardized, structured JSON error object to the client.

public record ApiErrorResponse(

        Instant timestamp,              // Exact time the error occurred (e.g. 2026-08-01T09:57:06Z)

        int status,                     // HTTP status code (e.g. 400, 404, 409, 500)

        String errorCode,               // Machine-readable error code (e.g. "JOB_NOT_FOUND")

        String message,                 // Human-readable error message

        String path,                    // The requested HTTP URI path (e.g. "/api/v1/jobs/123")

        Map<String, String> fieldErrors // Map of validation errors for specific input fields


) {

    public ApiErrorResponse {

        fieldErrors =
                fieldErrors == null
                        ? Map.of()
                        : Map.copyOf(fieldErrors);
    }
}