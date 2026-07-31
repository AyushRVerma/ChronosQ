package com.chronosq.api;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(

        Instant timestamp,

        int status,

        String errorCode,

        String message,

        String path,

        Map<String, String> fieldErrors

) {

    public ApiErrorResponse {

        fieldErrors =
                fieldErrors == null
                        ? Map.of()
                        : Map.copyOf(fieldErrors);
    }
}