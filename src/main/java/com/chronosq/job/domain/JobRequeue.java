package com.chronosq.job.domain;

import java.time.Instant;
import java.util.UUID;

public record JobRequeue(
        UUID id,
        UUID sourceJobId,
        UUID newJobId,
        String actor,
        String reason,
        Instant createdAt
) {
}
