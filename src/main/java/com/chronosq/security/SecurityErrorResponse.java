package com.chronosq.security;

import java.time.Instant;
import java.util.Objects;

import org.springframework.http.HttpStatus;


/*
 * Standard JSON response returned when authentication
 * or authorization fails.
 *
 * Authentication failure:
 *
 * - token is missing
 * - token is malformed
 * - token is expired
 * - token signature is invalid
 * - issuer or audience is invalid
 *
 * Authorization failure:
 *
 * - token is valid
 * - required scope is missing
 */
public record SecurityErrorResponse(

        /*
         * Exact UTC time when the error occurred.
         */
        Instant timestamp,

        /*
         * Numeric HTTP status.
         *
         * Examples:
         *
         * 401
         * 403
         */
        int status,

        /*
         * Human-readable HTTP error category.
         *
         * Examples:
         *
         * Unauthorized
         * Forbidden
         */
        String error,

        /*
         * Stable machine-readable error code.
         *
         * API clients should use this value when
         * making programmatic decisions.
         */
        String code,

        /*
         * Safe explanation for the API client.
         *
         * It must not expose private keys, secrets,
         * stack traces or internal validation details.
         */
        String message,

        /*
         * Request path that failed.
         *
         * Example:
         *
         * /api/v1/jobs
         */
        String path

) {

    private static final String
            AUTHENTICATION_REQUIRED_CODE =
            "AUTHENTICATION_REQUIRED";

    private static final String
            ACCESS_DENIED_CODE =
            "ACCESS_DENIED";


    /*
     * Compact record constructor.
     *
     * It validates every SecurityErrorResponse regardless
     * of which factory method creates it.
     */
    public SecurityErrorResponse {

        timestamp =
                Objects.requireNonNull(
                        timestamp,
                        "timestamp must not be null"
                );

        /*
         * This DTO is only intended for security errors.
         */
        if (
                status != HttpStatus.UNAUTHORIZED.value()
                        && status != HttpStatus.FORBIDDEN.value()
        ) {
            throw new IllegalArgumentException(
                    """
                    Security error status must be either \
                    401 or 403
                    """
            );
        }

        error =
                requireText(
                        error,
                        "error"
                );

        code =
                requireText(
                        code,
                        "code"
                );

        message =
                requireText(
                        message,
                        "message"
                );

        /*
         * A servlet request normally always has a path,
         * but this fallback protects the response model
         * from receiving null or blank values.
         */
        path =
                path == null || path.isBlank()
                        ? "/"
                        : path.trim();
    }


    /*
     * Creates a safe response for HTTP 401.
     *
     * We intentionally avoid telling the caller whether
     * a particular token was expired, malformed or had
     * an invalid signature.
     *
     * Revealing fewer internal details gives attackers
     * less information.
     */
    public static SecurityErrorResponse unauthorized(
            Instant timestamp,
            String path
    ) {

        HttpStatus status =
                HttpStatus.UNAUTHORIZED;

        return new SecurityErrorResponse(
                timestamp,
                status.value(),
                status.getReasonPhrase(),
                AUTHENTICATION_REQUIRED_CODE,
                """
                A valid bearer access token is required \
                to access this resource
                """,
                path
        );
    }


    /*
     * Creates a response for HTTP 403.
     *
     * The token was successfully authenticated, but
     * it did not contain the scope required by the API.
     */
    public static SecurityErrorResponse forbidden(
            Instant timestamp,
            String path
    ) {

        HttpStatus status =
                HttpStatus.FORBIDDEN;

        return new SecurityErrorResponse(
                timestamp,
                status.value(),
                status.getReasonPhrase(),
                ACCESS_DENIED_CODE,
                """
                The access token does not have permission \
                to access this resource
                """,
                path
        );
    }


    /*
     * Shared validation for required String fields.
     */
    private static String requireText(
            String value,
            String fieldName
    ) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName
                            + " must not be blank"
            );
        }

        return value.trim();
    }
}