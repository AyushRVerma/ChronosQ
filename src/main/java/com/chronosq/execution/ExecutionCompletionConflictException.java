package com.chronosq.execution;

import java.util.UUID;

public class ExecutionCompletionConflictException
        extends IllegalStateException {

    public ExecutionCompletionConflictException(
            UUID jobId,
            UUID executionId
    ) {
        super(
                """
                Job or execution was already changed \
                by another worker. Job ID: 
                """ + jobId
                        + ", Execution ID: "
                        + executionId
        );
    }
}