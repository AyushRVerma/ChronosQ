package com.chronosq.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.domain.ScheduleType;

import tools.jackson.databind.ObjectMapper;

class HttpWebhookJobHandlerTest {

    private PinnedWebhookClient webhookClient;
    private WebhookTargetValidator targetValidator;
    private HttpWebhookJobHandler handler;

    @BeforeEach
    void setUp() {
        webhookClient = mock(PinnedWebhookClient.class);
        targetValidator = mock(WebhookTargetValidator.class);

        handler = new HttpWebhookJobHandler(
                webhookClient,
                new ObjectMapper(),
                targetValidator
        );
    }

    @Test
    void shouldSupportHttpWebhookJobType() {
        assertThat(handler.jobType())
                .isEqualTo("HTTP_WEBHOOK");
    }

    @Test
    void shouldSendSuccessfulWebhookRequest() throws Exception {
        WebhookTargetValidator.ValidatedWebhookTarget target =
                validatedTarget();
        when(targetValidator.validate(any(URI.class)))
                .thenReturn(target);
        when(webhookClient.exchange(any(), any()))
                .thenReturn(204);

        Job job = createWebhookJob();

        assertThatCode(
                () -> handler.execute(job)
        ).doesNotThrowAnyException();

        verify(webhookClient).exchange(
                org.mockito.ArgumentMatchers.eq(target),
                any(HttpWebhookPayload.class)
        );
    }

    @Test
    void shouldThrowExceptionForUnsuccessfulResponse() throws Exception {
        when(targetValidator.validate(any(URI.class)))
                .thenReturn(validatedTarget());
        when(webhookClient.exchange(any(), any()))
                .thenReturn(502);

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

    private WebhookTargetValidator.ValidatedWebhookTarget validatedTarget()
            throws Exception {
        return new WebhookTargetValidator.ValidatedWebhookTarget(
                URI.create("https://example.com/api/orders"),
                new InetAddress[] {
                        InetAddress.getByAddress(new byte[] {
                                93, (byte) 184, (byte) 216, 34
                        })
                }
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
