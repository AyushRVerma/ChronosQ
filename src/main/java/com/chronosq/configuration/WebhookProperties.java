package com.chronosq.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chronosq.webhook")
public record WebhookProperties(

        @Min(100)
        @Max(60_000)
        int connectTimeoutMs, //5 seconds to establish connection

        @Min(100)
        @Max(300_000)
        int readTimeoutMs // 10 seconds to receive HTTP response body

) {
}