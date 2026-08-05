package com.chronosq.handler;

public class UnknownJobTypeException
        extends RuntimeException {

    public UnknownJobTypeException(
            String jobType
    ) {
        super(
                "No job handler is registered for job type: "
                        + jobType
        );
    }
}