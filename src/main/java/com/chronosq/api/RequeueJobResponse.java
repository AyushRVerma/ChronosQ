package com.chronosq.api;

import com.chronosq.job.domain.JobRequeue;
import java.util.UUID;

public record RequeueJobResponse(
        UUID sourceJobId,
        JobResponse job,
        JobRequeue audit
) { }
