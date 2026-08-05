package com.chronosq.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import tools.jackson.databind.ObjectMapper;

class PrintMessageJobHandlerTest {

    private static final Instant CURRENT_TIME =
            Instant.parse("2026-08-05T10:00:00Z");

    private PrintMessageJobHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PrintMessageJobHandler(
                new ObjectMapper()
        );
    }

    @Test
    void shouldSupportPrintMessageJobType() {
        assertThat(handler.jobType())
                .isEqualTo("PRINT_MESSAGE");
    }

    @Test
    void shouldExecuteValidPrintMessageJob() {
        Job job = createJob(
                "PRINT_MESSAGE",
                """
                {
                    "message": "Hello from ChronosQ"
                }
                """
        );

        assertThatCode(
                () -> handler.execute(job)
        ).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDifferentJobType() {
        Job job = createJob(
                "HTTP_WEBHOOK",
                """
                {
                    "message": "Hello"
                }
                """
        );

        assertThatThrownBy(
                () -> handler.execute(job)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "cannot process job type"
                );
    }

    @Test
    void shouldRejectBlankMessagePayload() {
        Job job = createJob(
                "PRINT_MESSAGE",
                """
                {
                    "message": "   "
                }
                """
        );

        assertThatThrownBy(
                () -> handler.execute(job)
        )
                .hasRootCauseInstanceOf(
                        IllegalArgumentException.class
                );
    }

    @Test
    void shouldRejectMalformedJsonPayload() {
        Job job = createJob(
                "PRINT_MESSAGE",
                "{not-valid-json}"
        );

        assertThatThrownBy(
                () -> handler.execute(job)
        ).isInstanceOf(Exception.class);
    }

    private Job createJob(
            String jobType,
            String payload
    ) {
        return new Job(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),
                "default",
                jobType,
                payload,
                JobStatus.RUNNING,
                0,
                CURRENT_TIME,
                ScheduleType.IMMEDIATE,
                null,
                1,
                3,
                null,
                "worker-1",
                CURRENT_TIME.plusSeconds(60),
                30,
                CURRENT_TIME,
                CURRENT_TIME,
                null,
                1L
        );
    }
}