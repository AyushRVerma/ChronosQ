package com.chronosq.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chronosq.recovery")

// records are immutable (they cannot be changed after creation), they are perfect for configuration
// data which should remain constant while the application is running.
public record RecoveryProperties(

        boolean enabled,

        @Min(1_000)
        @Max(300_000)
        long pollIntervalMs,

        @Min(1)
        @Max(1_000)
        int batchSize

) {
}