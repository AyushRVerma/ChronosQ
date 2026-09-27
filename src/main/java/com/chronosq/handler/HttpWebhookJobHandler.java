package com.chronosq.handler;

import java.net.URI;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.chronosq.job.domain.Job;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component

//HttpWebhookJobHandler processes jobs of type "HTTP_WEBHOOK".
// It uses Spring's RestClient to execute real outgoing HTTP calls (POST/PUT/PATCH) to external remote servers.
public class HttpWebhookJobHandler implements JobHandler {

    public static final String JOB_TYPE = "HTTP_WEBHOOK";

    private final PinnedWebhookClient webhookClient;
    private final ObjectMapper objectMapper;
    private final WebhookTargetValidator targetValidator;

    public HttpWebhookJobHandler(
            PinnedWebhookClient webhookClient,

            ObjectMapper objectMapper,
            WebhookTargetValidator targetValidator ) {

        this.webhookClient = Objects.requireNonNull(
                webhookClient,
                "webhookClient must not be null" );

        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null" );
        this.targetValidator = Objects.requireNonNull(
                targetValidator,
                "targetValidator must not be null"
        );
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void execute(Job job) throws Exception {
        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        if (!JOB_TYPE.equals(job.jobType())) {
            throw new IllegalArgumentException(
                    "HttpWebhookJobHandler cannot process job type: "
                            + job.jobType()
            );
        }
        //A: Deserialize Payload
        HttpWebhookPayload payload =
                objectMapper.readValue(
                        job.payload(),
                        HttpWebhookPayload.class
                );
        URI target = URI.create(payload.url());
        WebhookTargetValidator.ValidatedWebhookTarget validatedTarget =
                targetValidator.validate(target);

        int statusCode = webhookClient.exchange(validatedTarget, payload);

        //E: Validate HTTP Status Code
        if (statusCode < 200 || statusCode >= 300) {
            throw new WebhookDeliveryException(
                    statusCode
            );
        }

        log.info(
                "HTTP_WEBHOOK job completed: jobId={}, statusCode={}",
                job.id(),
                statusCode
        );
    }
}

//HttpWebhookJobHandler executes HTTP webhook jobs.
// It parses payload JSON, constructs outgoing HTTP POST/PUT/PATCH
// requests using Spring RestClient, sends custom headers and body data, and throws WebhookDeliveryException if the remote server returns a non-2xx status code
