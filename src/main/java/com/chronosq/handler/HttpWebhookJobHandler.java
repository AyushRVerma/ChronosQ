package com.chronosq.handler;

import java.net.URI;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.chronosq.job.domain.Job;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component

//HttpWebhookJobHandler processes jobs of type "HTTP_WEBHOOK".
// It uses Spring's RestClient to execute real outgoing HTTP calls (POST/PUT/PATCH) to external remote servers.
public class HttpWebhookJobHandler implements JobHandler {

    public static final String JOB_TYPE = "HTTP_WEBHOOK";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpWebhookJobHandler(
            @Qualifier("webhookRestClient")
            RestClient restClient,

            ObjectMapper objectMapper ) {

        this.restClient = Objects.requireNonNull(
                restClient,
                "restClient must not be null" );

        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null" );
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

        //B: Build HTTP Request
        RestClient.RequestBodySpec request =
                restClient
                        .method(payload.method()) //Build HTTP Request
                        .uri(URI.create(payload.url())) // Target URL
                        .headers(httpHeaders ->
                                payload.headers().forEach(
                                        httpHeaders::set  // Add custom headers
                                )
                        );

        //C: Attach JSON Request Body
        //If the payload contains a JSON body object, sets Content-Type: application/json and attaches the body.
        if (payload.body() != null
                && !payload.body().isNull()) {

            request
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload.body());
        }

        //D: Execute Outgoing HTTP Call & Capture Status Code
        int statusCode = request.exchange(
                (httpRequest, httpResponse) ->
                        httpResponse
                                .getStatusCode()
                                .value()
        );

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