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

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;


/*
 * Handles HTTP 401 authentication failures.
 *
 * It is called when:
 *
 * - no bearer token was provided
 * - the JWT is malformed
 * - the JWT is expired
 * - the signature is invalid
 * - the issuer is invalid
 * - the audience is invalid
 */
@Component
public final class JsonAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    private static final String
            WWW_AUTHENTICATE_VALUE =
            "Bearer realm=\"chronosq-api\"";

    private final ObjectMapper objectMapper;

    private final Clock clock;


    /*
     * Spring injects its configured ObjectMapper and
     * the application's Clock bean.
     */
    public JsonAuthenticationEntryPoint(
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
     * Spring Security calls this method when a request
     * cannot be authenticated.
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        Objects.requireNonNull(
                response,
                "response must not be null"
        );

        /*
         * A response that has already been committed
         * cannot safely be changed.
         */
        if (response.isCommitted()) {
            return;
        }

        Instant timestamp =
                clock.instant();

        SecurityErrorResponse errorResponse =
                SecurityErrorResponse.unauthorized(
                        timestamp,
                        request.getRequestURI()
                );

        /*
         * Return the correct authentication status.
         */
        response.setStatus(
                HttpStatus.UNAUTHORIZED.value()
        );

        /*
         * RFC-style bearer authentication challenge.
         *
         * This tells the client that the endpoint expects
         * an OAuth2 bearer access token.
         */
        response.setHeader(
                HttpHeaders.WWW_AUTHENTICATE,
                WWW_AUTHENTICATE_VALUE
        );

        /*
         * Security errors should not be stored in
         * browser or proxy caches.
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

        /*
         * Convert the SecurityErrorResponse record
         * into JSON and write it to the HTTP response.
         */
        objectMapper.writeValue(
                response.getOutputStream(),
                errorResponse
        );
    }
}