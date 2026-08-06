package com.chronosq.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chronosq.heartbeat")


// Heartbeat is the mechanism where active worker nodes periodically write to the worker_nodes table in PostgreSQL
// to prove they are still alive.
public record HeartbeatProperties(

        boolean enabled,

        @Min(1_000)
        @Max(60_000)
        long intervalMs

) {
}