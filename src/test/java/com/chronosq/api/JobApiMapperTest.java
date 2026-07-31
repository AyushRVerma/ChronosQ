package com.chronosq.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.chronosq.execution.ExecutionStatus;
import com.chronosq.execution.JobExecution;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JobApiMapperTest {

    private ObjectMapper objectMapper;

    private JobApiMapper jobApiMapper;

    @BeforeEach
    void setUp() {

        objectMapper = new ObjectMapper();

        jobApiMapper =
                new JobApiMapper(objectMapper);
    }

    @Test
    void shouldConvertJobToJobResponse()
            throws Exception {

        Job job = createJob();

        JobResponse response =
                jobApiMapper.toResponse(job);

        assertThat(response.id())
                .isEqualTo(job.id());

        assertThat(response.queueName())
                .isEqualTo(job.queueName());

        assertThat(response.jobType())
                .isEqualTo(job.jobType());

        assertThat(response.status())
                .isEqualTo(JobStatus.READY);

        assertThat(response.priority())
                .isEqualTo(5);

        assertThat(response.attemptCount())
                .isZero();

        assertThat(response.maxAttempts())
                .isEqualTo(3);

        JsonNode expectedPayload =
                objectMapper.readTree(
                        job.payload()
                );

        assertThat(response.payload())
                .isEqualTo(expectedPayload);
    }

    @Test
    void shouldConvertJsonPayloadToString()
            throws Exception {

        JsonNode payload =
                objectMapper.readTree(
                        """
                        {
                          "message": "Hello",
                          "count": 10
                        }
                        """
                );

        String payloadString =
                jobApiMapper.toPayloadString(
                        payload
                );

        JsonNode convertedPayload =
                objectMapper.readTree(
                        payloadString
                );

        assertThat(convertedPayload)
                .isEqualTo(payload);
    }

    @Test
    void shouldConvertExecutionToResponse() {

        JobExecution execution =
                createSuccessfulExecution();

        JobExecutionResponse response =
                jobApiMapper
                        .toExecutionResponse(
                                execution
                        );

        assertThat(response.id())
                .isEqualTo(execution.id());

        assertThat(response.jobId())
                .isEqualTo(execution.jobId());

        assertThat(response.workerId())
                .isEqualTo("worker-1");

        assertThat(response.attemptNumber())
                .isEqualTo(1);

        assertThat(response.status())
                .isEqualTo(
                        ExecutionStatus.SUCCEEDED
                );

        assertThat(response.durationMs())
                .isEqualTo(1_000L);

        assertThat(response.errorType())
                .isNull();

        assertThat(response.errorMessage())
                .isNull();
    }

    @Test
    void shouldConvertExecutionList() {

        JobExecution firstExecution =
                createFailedExecution();

        JobExecution secondExecution =
                createSuccessfulExecution();

        List<JobExecutionResponse> responses =
                jobApiMapper.toExecutionResponses(
                        List.of(
                                firstExecution,
                                secondExecution
                        )
                );

        assertThat(responses)
                .hasSize(2);

        assertThat(responses.get(0).status())
                .isEqualTo(
                        ExecutionStatus.FAILED
                );

        assertThat(responses.get(1).status())
                .isEqualTo(
                        ExecutionStatus.SUCCEEDED
                );
    }

    @Test
    void shouldRejectInvalidStoredPayload() {

        Job job = createJobWithPayload(
                "this is not valid JSON"
        );

        assertThatThrownBy(
                () -> jobApiMapper.toResponse(job)
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        job.id().toString()
                );
    }

    @Test
    void shouldRejectNullJob() {

        assertThatThrownBy(
                () -> jobApiMapper.toResponse(null)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "job must not be null"
                );
    }

    @Test
    void shouldRejectNullPayload() {

        assertThatThrownBy(
                () -> jobApiMapper
                        .toPayloadString(null)
        )
                .isInstanceOf(
                        NullPointerException.class
                )
                .hasMessage(
                        "payload must not be null"
                );
    }

    private Job createJob() {

        return createJobWithPayload(
                """
                {
                  "message": "Hello from ChronosQ"
                }
                """
        );
    }

    private Job createJobWithPayload(
            String payload
    ) {

        Instant now =
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                );

        return new Job(
                UUID.randomUUID(),
                "default",
                "PRINT_MESSAGE",
                payload,
                JobStatus.READY,
                5,
                now,
                ScheduleType.IMMEDIATE,
                null,
                0,
                3,
                "request-101",
                null,
                null,
                30,
                now,
                now,
                null,
                0L
        );
    }

    private JobExecution
    createSuccessfulExecution() {

        return new JobExecution(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "worker-1",
                1,
                ExecutionStatus.SUCCEEDED,
                Instant.parse(
                        "2026-01-01T10:01:00Z"
                ),
                Instant.parse(
                        "2026-01-01T10:01:01Z"
                ),
                1_000L,
                null,
                null
        );
    }

    private JobExecution
    createFailedExecution() {

        return new JobExecution(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "worker-2",
                1,
                ExecutionStatus.FAILED,
                Instant.parse(
                        "2026-01-01T10:02:00Z"
                ),
                Instant.parse(
                        "2026-01-01T10:02:02Z"
                ),
                2_000L,
                "NETWORK_ERROR",
                "Remote service was unavailable"
        );
    }
}