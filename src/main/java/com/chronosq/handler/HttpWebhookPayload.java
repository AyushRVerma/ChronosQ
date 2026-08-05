package com.chronosq.handler;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpMethod;

import tools.jackson.databind.JsonNode;


//A Webhook job tells ChronosQ: "Make an outgoing HTTP call (POST/PUT/PATCH)
// to a remote server with a specific URL, headers, and JSON body."

public record HttpWebhookPayload(

        String url,

        HttpMethod method,

        Map<String, String> headers,

        JsonNode body

) {

    private static final int MAX_URL_LENGTH = 2_048;
    private static final int MAX_HEADER_COUNT = 50;

    private static final Set<HttpMethod> ALLOWED_METHODS = Set.of(
                    HttpMethod.POST,
                    HttpMethod.PUT,
                    HttpMethod.PATCH
            );

    public HttpWebhookPayload {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook URL must not be blank"
            );
        }

        url = url.trim();

        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(
                    "Webhook URL must not exceed "
                            + MAX_URL_LENGTH
                            + " characters"
            );
        }

        URI uri;

        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Webhook URL is invalid",
                    exception
            );
        }

        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {

            throw new IllegalArgumentException(
                    "Webhook URL must use HTTP or HTTPS"
            );
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook URL must contain a valid host"
            );
        }

        method = (method == null)
                ? HttpMethod.POST
                : method;

        if (!ALLOWED_METHODS.contains(method)) {
            throw new IllegalArgumentException(
                    "Webhook method must be POST, PUT or PATCH"
            );
        }

        Map<String, String> suppliedHeaders =
                (headers == null)
                        ? Map.of()
                        : headers;

        if (suppliedHeaders.size() > MAX_HEADER_COUNT) {
            throw new IllegalArgumentException(
                    "Webhook must not contain more than "
                            + MAX_HEADER_COUNT
                            + " headers"
            );
        }

        suppliedHeaders.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "Webhook header name must not be blank"
                );
            }

            if (value == null) {
                throw new IllegalArgumentException(
                        "Webhook header value must not be null"
                );
            }
        });

        headers = Map.copyOf(suppliedHeaders);
    }
}

// HttpWebhookPayload is a robust, security-hardened DTO record for webhook jobs.
// It enforces HTTP/HTTPS scheme security, restricts HTTP methods to POST/PUT/PATCH,
// validates target hostnames, and caps headers & URL lengths to protect worker nodes from
// malicious or malformed webhook calls.