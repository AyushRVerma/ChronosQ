package com.chronosq.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

//1
import org.springframework.boot.context.properties
        .ConfigurationProperties;

import org.springframework.validation.annotation
        .Validated;

@Validated
@ConfigurationProperties(
        prefix = "chronosq.worker"
)

//WorkerProperties configures the Worker Engine — the component that actually picks up READY jobs from a queue and executes them!
public record WorkerProperties(

        boolean enabled,   // Toggle switch to turn this worker node on or off.

        @NotBlank(
                message = "workerId must not be blank"
        )
        @Size(
                max = 200,
                message = """
                        workerId must not exceed \
                        200 characters
                        """
        )
        String workerId,

        @NotBlank(
                message = "instanceName must not be blank"
        )
        @Size(
                max = 200,
                message = """
                        instanceName must not exceed \
                        200 characters
                        """
        )
        String instanceName,

        @NotBlank(
                message = "queueName must not be blank"
        )
        @Size(
                max = 100,
                message = """
                        queueName must not exceed \
                        100 characters
                        """
        )
        String queueName,  //Tells the worker which queue to pull jobs from\
        //By configuring queueName per worker, you can dedicate 10 fast servers to "payment-queue" and 2 slower servers to "email-queue". This ensures heavy email processing never slows down critical payment processing

        @Min(
                value = 1,
                message = """
                        claimBatchSize must be \
                        at least 1
                        """
        )
        @Max(
                value = 1_000,
                message = """
                        claimBatchSize must not \
                        exceed 1000
                        """
        )
        int claimBatchSize,  // Controls how many jobs this worker claims in a single DB query

        @Min(
                value = 5,
                message = """
                        leaseDurationSeconds must \
                        be at least 5
                        """
        )
        @Max(
                value = 86_400,
                message = """
                        leaseDurationSeconds must \
                        not exceed 86400
                        """
        )
        long leaseDurationSeconds  //  It's the lock duration. When a worker claims a job, it locks the job for 60 seconds. If the worker crashes, the lock automatically expires after 60 seconds so another worker can rescue the job!

) {

        //WorkerProperties is a strongly-typed, validated configuration record that controls how an active worker node identifies itself, which queue it listens to, how many jobs it claims per query, and how long it locks claimed jobs!
}