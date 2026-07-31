package com.chronosq.api;

import java.util.List;
import java.util.Objects;

import com.chronosq.execution.JobExecution;
import com.chronosq.job.domain.Job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


//This class bridges your internal domain layer (Job)
// and your public API layer (JobResponse / SubmitJobRequest).

@Component
@RequiredArgsConstructor
public final class JobApiMapper {

    //handle JSON parsing and serialization
    private final ObjectMapper objectMapper;

    // Converts an internal Job domain object into a JobResponse DTO ready to send over HTTP.

    public JobResponse toResponse(Job job) {

        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        return new JobResponse(
                job.id(),
                job.queueName(),
                job.jobType(),
                readPayload(job),
                job.status(),
                job.priority(),
                job.availableAt(),
                job.scheduleType(),
                job.intervalSeconds(),
                job.attemptCount(),
                job.maxAttempts(),
                job.idempotencyKey(),
                job.timeoutSeconds(),
                job.createdAt(),
                job.updatedAt(),
                job.completedAt()
        );
    }

    //Used when a client submits a new job. It converts a parsed JsonNode from SubmitJobRequest
    // into a raw String to be saved into PostgreSQL's JSONB column.
    public String toPayloadString(
            JsonNode payload
    ) {

        Objects.requireNonNull(
                payload,
                "payload must not be null"
        );

        try {
            return objectMapper.writeValueAsString(
                    payload
            );
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Unable to convert payload to JSON",
                    exception
            );
        }
    }

    //Converts the raw JSON String stored in PostgreSQL back into a structured JsonNode object for JobResponse.
    private JsonNode readPayload(Job job) {

        try {
            return objectMapper.readTree(
                    job.payload()
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Stored payload is invalid for job: "
                            + job.id(),
                    exception
            );
        }
    }

    // Converts a single JobExecution domain record into a JobExecutionResponse API DTO.
    public JobExecutionResponse toExecutionResponse(
            JobExecution execution
    ) {

        Objects.requireNonNull(
                execution,
                "execution must not be null"
        );

        return new JobExecutionResponse(
                execution.id(),
                execution.jobId(),
                execution.workerId(),
                execution.attemptNumber(),
                execution.status(),
                execution.startedAt(),
                execution.finishedAt(),
                execution.durationMs(),
                execution.errorType(),
                execution.errorMessage()
        );
    }

    public List<JobExecutionResponse> toExecutionResponses(
            List<JobExecution> executions
    ) {

        Objects.requireNonNull(
                executions,
                "executions must not be null"
        );

        return executions.stream()
                .map(this::toExecutionResponse)
                .toList();
    }
}
// JobApiMapper ensures your domain logic deals with pure Java Strings,
// PostgreSQL deals with JSONB, and API clients deal with rich, nested JSON objects — keeping all layers decoupled and clean!

//                       INCOMING REQUEST (POST /api/jobs)
//                                       │
//                                       ▼
//                              SubmitJobRequest
//                               payload: JsonNode
//                                       │
//                                       │ JobApiMapper.toPayloadString(payload)
//                                       ▼
//                                   Job (Domain)
//                                payload: String
//                                       │
//                                       ▼
//                             PostgreSQL (JSONB column)
//                                       │
//                                       ▼
//                                   Job (Domain)
//                                payload: String
//                                       │
//                                       │ JobApiMapper.toResponse(job)
//                                       ▼
//                              JobResponse (API DTO)
//                               payload: JsonNode
//                                       │
//                                       ▼
//                        OUTGOING HTTP RESPONSE (200 OK)