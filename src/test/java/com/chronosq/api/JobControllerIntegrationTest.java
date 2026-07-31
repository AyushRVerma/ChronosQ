package com.chronosq.api;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet
        .request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet
        .request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.header;

import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.testcontainers
        .service.connection.ServiceConnection;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.testcontainers.postgresql
        .PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class JobControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgresContainer =
            new PostgreSQLContainer(
                    "postgres:17-alpine"
            );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {

        jdbcClient.sql("""
                DELETE FROM job_executions
                """)
                .update();

        jdbcClient.sql("""
                DELETE FROM jobs
                """)
                .update();

        jdbcClient.sql("""
                DELETE FROM worker_nodes
                """)
                .update();
    }

    @Test
    void shouldSubmitAndRetrieveImmediateJob()
            throws Exception {

        String requestBody = """
                {
                  "queueName": "default",
                  "jobType": "PRINT_MESSAGE",
                  "payload": {
                    "message": "Hello from ChronosQ"
                  },
                  "priority": 5,
                  "scheduleType": "IMMEDIATE",
                  "maxAttempts": 3,
                  "timeoutSeconds": 30,
                  "idempotencyKey": "request-101"
                }
                """;

        MvcResult submissionResult =
                mockMvc.perform(
                                post("/api/v1/jobs")
                                        .contentType(
                                                MediaType
                                                        .APPLICATION_JSON
                                        )
                                        .content(requestBody)
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andExpect(
                                header().exists(
                                        "Location"
                                )
                        )
                        .andExpect(
                                jsonPath("$.id")
                                        .exists()
                        )
                        .andExpect(
                                jsonPath("$.queueName")
                                        .value("default")
                        )
                        .andExpect(
                                jsonPath("$.jobType")
                                        .value(
                                                "PRINT_MESSAGE"
                                        )
                        )
                        .andExpect(
                                jsonPath(
                                        "$.payload.message"
                                )
                                        .value(
                                                "Hello from ChronosQ"
                                        )
                        )
                        .andExpect(
                                jsonPath("$.status")
                                        .value("READY")
                        )
                        .andExpect(
                                jsonPath("$.priority")
                                        .value(5)
                        )
                        .andExpect(
                                jsonPath("$.attemptCount")
                                        .value(0)
                        )
                        .andReturn();

        JsonNode submittedJson =
                objectMapper.readTree(
                        submissionResult
                                .getResponse()
                                .getContentAsString()
                );

        String jobId =
                submittedJson
                        .get("id")
                        .asText();

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}",
                                jobId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(jobId)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("READY")
                )
                .andExpect(
                        jsonPath("$.idempotencyKey")
                                .value("request-101")
                );
    }

    @Test
    void shouldRejectInvalidSubmission()
            throws Exception {

        String invalidRequest = """
                {
                  "queueName": "",
                  "jobType": "",
                  "payload": null,
                  "scheduleType": "IMMEDIATE",
                  "maxAttempts": 0
                }
                """;

        mockMvc.perform(
                        post("/api/v1/jobs")
                                .contentType(
                                        MediaType
                                                .APPLICATION_JSON
                                )
                                .content(invalidRequest)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "VALIDATION_FAILED"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.queueName"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.jobType"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.payload"
                        ).exists()
                )
                .andExpect(
                        jsonPath(
                                "$.fieldErrors.maxAttempts"
                        ).exists()
                );
    }

    @Test
    void shouldReturnNotFoundForMissingJob()
            throws Exception {

        UUID missingJobId =
                UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}",
                                missingJobId
                        )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "JOB_NOT_FOUND"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/api/v1/jobs/"
                                                + missingJobId
                                )
                );
    }

    @Test
    void shouldReturnSameJobForRepeatedIdempotencyKey()
            throws Exception {

        String requestBody = """
                {
                  "queueName": "default",
                  "jobType": "PRINT_MESSAGE",
                  "payload": {
                    "message": "Only create once"
                  },
                  "scheduleType": "IMMEDIATE",
                  "idempotencyKey": "same-request-101"
                }
                """;

        MvcResult firstResult =
                submitJob(requestBody);

        MvcResult secondResult =
                submitJob(requestBody);

        String firstJobId =
                readJobId(firstResult);

        String secondJobId =
                readJobId(secondResult);

        assertThat(secondJobId)
                .isEqualTo(firstJobId);

        Integer jobCount =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM jobs
                        WHERE idempotency_key =
                            :idempotencyKey
                        """)
                        .param(
                                "idempotencyKey",
                                "same-request-101"
                        )
                        .query(Integer.class)
                        .single();

        assertThat(jobCount)
                .isEqualTo(1);
    }

    @Test
    void shouldCancelReadyJob()
            throws Exception {

        String requestBody = """
                {
                  "queueName": "default",
                  "jobType": "PRINT_MESSAGE",
                  "payload": {
                    "message": "Cancel me"
                  },
                  "scheduleType": "IMMEDIATE"
                }
                """;

        MvcResult submissionResult =
                submitJob(requestBody);

        String jobId =
                readJobId(submissionResult);

        mockMvc.perform(
                        post(
                                "/api/v1/jobs/{jobId}/cancel",
                                jobId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(jobId)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("CANCELLED")
                )
                .andExpect(
                        jsonPath("$.completedAt")
                                .exists()
                );
    }

    @Test
    void shouldReturnEmptyExecutionHistory()
            throws Exception {

        String requestBody = """
                {
                  "queueName": "default",
                  "jobType": "PRINT_MESSAGE",
                  "payload": {
                    "message": "Not executed yet"
                  },
                  "scheduleType": "IMMEDIATE"
                }
                """;

        MvcResult submissionResult =
                submitJob(requestBody);

        String jobId =
                readJobId(submissionResult);

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{jobId}/executions",
                                jobId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$")
                                .isEmpty()
                );
    }

    @Test
    void shouldRejectMalformedJson()
            throws Exception {

        String malformedJson = """
                {
                  "queueName": "default",
                  "jobType":
                }
                """;

        mockMvc.perform(
                        post("/api/v1/jobs")
                                .contentType(
                                        MediaType
                                                .APPLICATION_JSON
                                )
                                .content(malformedJson)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "INVALID_REQUEST_BODY"
                                )
                );
    }

    private MvcResult submitJob(
            String requestBody
    ) throws Exception {

        return mockMvc.perform(
                        post("/api/v1/jobs")
                                .contentType(
                                        MediaType
                                                .APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(
                        status().isCreated()
                )
                .andReturn();
    }

    private String readJobId(
            MvcResult result
    ) throws Exception {

        JsonNode responseJson =
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString()
                );

        return responseJson
                .get("id")
                .asText();
    }
}