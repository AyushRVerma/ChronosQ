package com.chronosq.metrics;

import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronosq.execution.ExecutionResult;
import com.chronosq.execution.ExecutionStatus;

@ExtendWith(MockitoExtension.class)
class JobExecutionObserverTest {

    @Mock
    private ChronosQMetrics chronosQMetrics;

    private JobExecutionObserver observer;

    @BeforeEach
    void setUp() {
        observer = new JobExecutionObserver(
                chronosQMetrics,
                new AfterCommitExecutor()
        );
    }

    @Test
    void shouldRecordCompletedExecution() {
        ExecutionResult executionResult =
                new ExecutionResult(
                        ExecutionStatus.SUCCEEDED,
                        Instant.parse(
                                "2026-08-07T10:00:01Z"
                        ),
                        250,
                        null,
                        null
                );

        observer.recordCompletedExecution(
                executionResult
        );

        verify(chronosQMetrics)
                .recordExecution(
                        ExecutionStatus.SUCCEEDED,
                        250
                );
    }

    @Test
    void shouldRecordFailedExecution() {
        ExecutionResult executionResult =
                new ExecutionResult(
                        ExecutionStatus.FAILED,
                        Instant.parse(
                                "2026-08-07T10:00:01Z"
                        ),
                        500,
                        "IllegalStateException",
                        "Handler failed"
                );

        observer.recordCompletedExecution(
                executionResult
        );

        verify(chronosQMetrics)
                .recordExecution(
                        ExecutionStatus.FAILED,
                        500
                );
    }
}