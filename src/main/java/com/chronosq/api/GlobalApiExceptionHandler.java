package com.chronosq.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.chronosq.job.domain
        .InvalidJobStateTransitionException;

import com.chronosq.job.service
        .ConcurrentJobModificationException;

import com.chronosq.job.service
        .JobNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.http.converter
        .HttpMessageNotReadableException;

import org.springframework.web.bind
        .MethodArgumentNotValidException;

import org.springframework.web.bind.annotation
        .ExceptionHandler;

import org.springframework.web.bind.annotation
        .RestControllerAdvice;

import org.springframework.web.method.annotation
        .MethodArgumentTypeMismatchException;




// This class is the central safety net for your entire REST API.
//
// Annotated with @RestControllerAdvice, it intercepts exceptions thrown anywhere in your
// controllers or services and converts them into the standardized ApiErrorResponse JSON
// structure we just discussed.
@RestControllerAdvice

public class GlobalApiExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    GlobalApiExceptionHandler.class
            );

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleJobNotFound(JobNotFoundException exception, HttpServletRequest request) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "JOB_NOT_FOUND",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(InvalidJobStateTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStateTransition(
            InvalidJobStateTransitionException exception,
            HttpServletRequest request ) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "INVALID_JOB_STATE_TRANSITION",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(ConcurrentJobModificationException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentModification(
            ConcurrentJobModificationException exception,
            HttpServletRequest request ) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "CONCURRENT_JOB_MODIFICATION",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException exception,
            HttpServletRequest request ) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // // 1. Collect field-level validation errors (@NotBlank, @Min, @Max)
        exception.getBindingResult()
                .getFieldErrors()
                .forEach(
                        error -> fieldErrors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        // 2. Collect class-level validation errors (@AssertTrue)
        exception.getBindingResult()
                .getGlobalErrors()
                .forEach(
                        error -> fieldErrors.put(
                                error.getObjectName(),
                                error.getDefaultMessage()
                        )
                );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "The request contains invalid values",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                """
                The request body is missing or \
                contains invalid JSON
                """,
                request,
                Map.of()
        );
    }

    @ExceptionHandler(
            MethodArgumentTypeMismatchException.class
    )
    public ResponseEntity<ApiErrorResponse>
    handleTypeMismatch(

            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {

        String message =
                "Invalid value for parameter: "
                        + exception.getName();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                message,
                request,
                Map.of()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse>
    handleIllegalArgument(

            IllegalArgumentException exception,
            HttpServletRequest request
    ) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(
            Exception exception,
            HttpServletRequest request
    ) {
         // Log full stack trace securely on the server
        logger.error(
                "Unexpected API error for path {}",
                request.getRequestURI(),
                exception
        );

        // Return generic message to client without leaking sensitive stack details
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request,
                Map.of()
        );
    }

    // Centralizes response creation so every error handler follows the exact
    // same format and structure!
    private ResponseEntity<ApiErrorResponse>
    buildResponse(

            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {

        ApiErrorResponse response =
                new ApiErrorResponse(
                        Instant.now(),
                        status.value(),
                        errorCode,
                        message,
                        request.getRequestURI(),
                        fieldErrors
                );

        return ResponseEntity
                .status(status)
                .body(response);
    }
}