package com.chronosq.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import tools.jackson.databind.ObjectMapper;

class HttpWebhookJobHandlerTest {

    private MockRestServiceServer mockServer;
    private HttpWebhookJobHandler handler;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder =
                RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(restClientBuilder)
                .build();

        handler = new HttpWebhookJobHandler(
                restClientBuilder.build(),
                new ObjectMapper()
        );
    }

    @Test
    void shouldSupportHttpWebhookJobType() {
        assertThat(handler.jobType())
                .isEqualTo("HTTP_WEBHOOK");
    }

    @Test
    void shouldSendSuccessfulWebhookRequest() {
        mockServer.expect(
                        requestTo(
                                "https://example.com/api/orders"
                        )
                )
                .andExpect(
                        method(HttpMethod.POST)
                )
                .andExpect(
                        header(
                                "X-Source",
                                "ChronosQ"
                        )
                )
                .andExpect(
                        content().json(
                                """
                                {
                                    "orderId": "order-1001",
                                    "status": "CREATED"
                                }
                                """
                        )
                )
                .andRespond(
                        withStatus(HttpStatus.NO_CONTENT)
                );

        Job job = createWebhookJob();

        assertThatCode(
                () -> handler.execute(job)
        ).doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void shouldThrowExceptionForUnsuccessfulResponse() {
        mockServer.expect(
                        requestTo(
                                "https://example.com/api/orders"
                        )
                )
                .andRespond(
                        withStatus(HttpStatus.BAD_GATEWAY)
                );

        Job job = createWebhookJob();

        assertThatThrownBy(
                () -> handler.execute(job)
        )
                .isInstanceOf(
                        WebhookDeliveryException.class
                )
                .satisfies(exception -> {
                    WebhookDeliveryException webhookException =
                            (WebhookDeliveryException) exception;

                    assertThat(
                            webhookException.statusCode()
                    ).isEqualTo(502);

                    assertThat(
                            webhookException.isRetryable()
                    ).isTrue();
                });

        mockServer.verify();
    }

    @Test
    void shouldRejectJobForDifferentHandlerType() {
        Job job = createJob(
                "PRINT_MESSAGE",
                """
                {
                    "message": "Hello"
                }
                """
        );

        assertThatThrownBy(
                () -> handler.execute(job)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "cannot process job type"
                );
    }

    private Job createWebhookJob() {
        return createJob(
                "HTTP_WEBHOOK",
                """
                {
                    "url": "https://example.com/api/orders",
                    "method": "POST",
                    "headers": {
                        "X-Source": "ChronosQ"
                    },
                    "body": {
                        "orderId": "order-1001",
                        "status": "CREATED"
                    }
                }
                """
        );
    }

    private Job createJob(
            String jobType,
            String payload
    ) {
        Instant currentTime =
                Instant.parse("2026-08-05T10:00:00Z");

        return new Job(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),
                "default",
                jobType,
                payload,
                JobStatus.RUNNING,
                0,
                currentTime,
                ScheduleType.IMMEDIATE,
                null,
                1,
                3,
                null,
                "worker-1",
                currentTime.plusSeconds(60),
                30,
                currentTime,
                currentTime,
                null,
                1L
        );
    }
}