package com.chronosq.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

import java.util.List;

import com.chronosq.job.domain.Job;

import org.junit.jupiter.api.Test;

class JobHandlerRegistryTest {

    @Test
    void shouldReturnRegisteredHandler() {

        JobHandler printMessageHandler =
                new TestJobHandler(
                        "PRINT_MESSAGE"
                );

        JobHandlerRegistry registry =
                new JobHandlerRegistry(
                        List.of(printMessageHandler)
                );

        JobHandler result =
                registry.getRequiredHandler(
                        "PRINT_MESSAGE"
                );

        assertThat(result)
                .isSameAs(printMessageHandler);
    }

    @Test
    void shouldReportSupportedJobType() {

        JobHandlerRegistry registry =
                new JobHandlerRegistry(
                        List.of(
                                new TestJobHandler(
                                        "PRINT_MESSAGE"
                                )
                        )
                );

        assertThat(
                registry.supports("PRINT_MESSAGE")
        ).isTrue();

        assertThat(
                registry.supports("HTTP_WEBHOOK")
        ).isFalse();

        assertThat(
                registry.supports(null)
        ).isFalse();
    }

    @Test
    void shouldRejectUnknownJobType() {

        JobHandlerRegistry registry =
                new JobHandlerRegistry(
                        List.of(
                                new TestJobHandler(
                                        "PRINT_MESSAGE"
                                )
                        )
                );

        assertThatThrownBy(
                () -> registry.getRequiredHandler(
                        "UNKNOWN_JOB"
                )
        )
                .isInstanceOf(
                        UnknownJobTypeException.class
                )
                .hasMessageContaining(
                        "UNKNOWN_JOB"
                );
    }

    @Test
    void shouldRejectDuplicateJobType() {

        assertThatThrownBy(
                () -> new JobHandlerRegistry(
                        List.of(
                                new TestJobHandler(
                                        "PRINT_MESSAGE"
                                ),
                                new TestJobHandler(
                                        "PRINT_MESSAGE"
                                )
                        )
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "Multiple handlers"
                );
    }

    @Test
    void shouldRejectBlankJobType() {

        assertThatThrownBy(
                () -> new JobHandlerRegistry(
                        List.of(
                                new TestJobHandler(" ")
                        )
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "jobType"
                );
    }

    private static class TestJobHandler
            implements JobHandler {

        private final String jobType;

        private TestJobHandler(
                String jobType
        ) {
            this.jobType = jobType;
        }

        @Override
        public String jobType() {
            return jobType;
        }

        @Override
        public void execute(Job job) {
            // This fake handler is only used for registry tests.
        }
    }
}