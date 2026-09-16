package com.chronosq.security;

import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.time.Clock;
import java.time.Instant;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;


/*
 * Handles HTTP 403 authorization failures.
 *
 * It is called when:
 *
 * - the JWT is valid
 * - the client is authenticated
 * - but the token does not contain the required scope
 *
 * Example:
 *
 * Token contains:
 * jobs.read
 *
 * Endpoint requires:
 * jobs.submit
 *
 * Result:
 * HTTP 403 Forbidden
 */
@Component
public final class JsonAccessDeniedHandler
        implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    private final Clock clock;


    /*
     * Dependencies are injected through the constructor.
     */
    public JsonAccessDeniedHandler(
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper must not be null"
                );

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }


    /*
     * Spring Security calls this method when the client
     * is authenticated but does not have permission.
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {

        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        Objects.requireNonNull(
                response,
                "response must not be null"
        );

        if (response.isCommitted()) {
            return;
        }

        Instant timestamp =
                clock.instant();

        SecurityErrorResponse errorResponse =
                SecurityErrorResponse.forbidden(
                        timestamp,
                        request.getRequestURI()
                );

        response.setStatus(
                HttpStatus.FORBIDDEN.value()
        );

        /*
         * Do not cache authorization failure responses.
         */
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "no-store"
        );

        response.setHeader(
                HttpHeaders.PRAGMA,
                "no-cache"
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        objectMapper.writeValue(
                response.getOutputStream(),
                errorResponse
        );
    }
}