package com.chronosq.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
//This is the configuration record for the Worker Execution Thread Pool!
@Validated
@ConfigurationProperties(prefix = "chronosq.execution")
public record ExecutionProperties(

        @Min(100)
        long pollIntervalMs,

        @Min(1)
        @Max(100)
        int threadCount,

        @Min(1)
        @Max(10_000)
        int queueCapacity

) {
}
// ExecutionProperties configures the multi-threaded execution engine inside the worker node!