package com.chronosq.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties
        .ConfigurationProperties;

import org.springframework.validation.annotation
        .Validated;

//Configuration layer
// SchedulerProperties uses Spring Boot's @ConfigurationProperties feature to bind
// application settings from application.yml directly into a strongly-typed
// Java record — with built-in validation!

@Validated
@ConfigurationProperties(
        prefix = "chronosq.scheduler"
)
public record SchedulerProperties(

        boolean enabled,

        @Min(
                value = 100,
                message = """
                        scheduler poll interval must \
                        be at least 100 milliseconds
                        """
        )
        long pollIntervalMs,

        @Min(
                value = 1,
                message = """
                        scheduler batch size must \
                        be at least 1
                        """
        )
        @Max(
                value = 1_000,
                message = """
                        scheduler batch size must \
                        not exceed 1000
                        """
        )
        int batchSize

) {
}