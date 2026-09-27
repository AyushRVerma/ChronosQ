package com.chronosq.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequeueJobRequest(
        @NotBlank @Size(max = 500) String reason
) { }
