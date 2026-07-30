package com.chronosq.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JobStateMachineTest {

    @ParameterizedTest(name = "{0} -> {1} should be valid")
    @MethodSource("validTransitions")
    void shouldAllowValidTransitions(
            JobStatus currentStatus,
            JobStatus newStatus
    ) {
        Assertions.assertThat(
                JobStateMachine.canTransition(currentStatus, newStatus)
        ).isTrue();

        assertThatCode(
                () -> JobStateMachine.validateTransition(
                        currentStatus,
                        newStatus
                )
        ).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} should be invalid")
    @MethodSource("invalidTransitions")
    void shouldRejectInvalidTransitions(
            JobStatus currentStatus,
            JobStatus newStatus
    ) {
        assertThat(
                JobStateMachine.canTransition(currentStatus, newStatus)
        ).isFalse();

        assertThatThrownBy(
                () -> JobStateMachine.validateTransition(
                        currentStatus,
                        newStatus
                )
        )
                .isInstanceOf(InvalidJobStateTransitionException.class)
                .hasMessage(
                        "Invalid job status transition: "
                                + currentStatus
                                + " -> "
                                + newStatus
                );
    }

    @Test
    void shouldRejectNullCurrentStatus() {
        assertThatThrownBy(
                () -> JobStateMachine.canTransition(
                        null,
                        JobStatus.READY
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Current job status must not be null");
    }

    @Test
    void shouldRejectNullNewStatus() {
        assertThatThrownBy(
                () -> JobStateMachine.canTransition(
                        JobStatus.READY,
                        null
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("New job status must not be null");
    }

    private static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(
                        JobStatus.SCHEDULED,
                        JobStatus.READY
                ),
                Arguments.of(
                        JobStatus.SCHEDULED,
                        JobStatus.CANCELLED
                ),
                Arguments.of(
                        JobStatus.READY,
                        JobStatus.RUNNING
                ),
                Arguments.of(
                        JobStatus.READY,
                        JobStatus.CANCELLED
                ),
                Arguments.of(
                        JobStatus.RUNNING,
                        JobStatus.SUCCEEDED
                ),
                Arguments.of(
                        JobStatus.RUNNING,
                        JobStatus.RETRY_WAIT
                ),
                Arguments.of(
                        JobStatus.RUNNING,
                        JobStatus.DEAD_LETTERED
                ),
                Arguments.of(
                        JobStatus.RETRY_WAIT,
                        JobStatus.READY
                ),
                Arguments.of(
                        JobStatus.RETRY_WAIT,
                        JobStatus.CANCELLED
                )
        );
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(
                        JobStatus.SCHEDULED,
                        JobStatus.RUNNING
                ),
                Arguments.of(
                        JobStatus.READY,
                        JobStatus.SUCCEEDED
                ),
                Arguments.of(
                        JobStatus.RUNNING,
                        JobStatus.CANCELLED
                ),
                Arguments.of(
                        JobStatus.RETRY_WAIT,
                        JobStatus.RUNNING
                ),
                Arguments.of(
                        JobStatus.SUCCEEDED,
                        JobStatus.READY
                ),
                Arguments.of(
                        JobStatus.DEAD_LETTERED,
                        JobStatus.READY
                ),
                Arguments.of(
                        JobStatus.CANCELLED,
                        JobStatus.READY
                ),
                Arguments.of(
                        JobStatus.READY,
                        JobStatus.READY
                ),
                Arguments.of(
                        JobStatus.RUNNING,
                        JobStatus.RUNNING
                )
        );
    }

    @Test
    void exceptionShouldExposeTransitionDetails() {
        InvalidJobStateTransitionException exception =
                new InvalidJobStateTransitionException(
                        JobStatus.SUCCEEDED,
                        JobStatus.READY
                );

        assertThat(exception.currentStatus())
                .isEqualTo(JobStatus.SUCCEEDED);

        assertThat(exception.requestedStatus())
                .isEqualTo(JobStatus.READY);

        assertThat(exception)
                .hasMessage(
                        "Invalid job status transition: "
                                + "SUCCEEDED -> READY"
                );
    }
}