package com.chronosq.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        Instant generatedAt,
        Summary summary,
        List<Worker> workers,
        List<RecentJob> recentJobs,
        List<PerformancePoint> performance
) {

    public record Summary(
            long queued,
            long running,
            long completedLastHour,
            long retrying,
            long retryAttemptsLastHour,
            long deadLetter
    ) {
    }

    public record Worker(
            String workerId,
            String instanceName,
            String status,
            Instant lastHeartbeatAt,
            Instant startedAt,
            long activeLeases
    ) {
    }

    public record RecentJob(
            UUID jobId,
            String jobType,
            String queueName,
            String jobStatus,
            int attemptCount,
            int maxAttempts,
            Instant availableAt,
            String lockedBy,
            Instant leaseExpiresAt,
            UUID executionId,
            String executionStatus,
            String workerId,
            Integer attemptNumber,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            String errorType,
            String errorMessage
    ) {
    }

    public record PerformancePoint(
            Instant bucket,
            long completed,
            long p95StartLatencyMs
    ) {
    }
}
