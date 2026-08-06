package com.chronosq.configuration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chronosq.retry")


//When a job fails (e.g. temporary network glitch or DB timeout),
//ChronosQ doesn't retry it instantly — it waits for a calculated delay using
//Exponential Backoff with Jitter.
public record RetryProperties(

        boolean enabled,

        @Min(100)
        @Max(60_000)
        long initialDelayMs,

        @Min(100)
        @Max(3_600_000)
        long maximumDelayMs,

        @DecimalMin("1.0")
        @DecimalMax("10.0")
        double multiplier,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        double jitterFactor

) {

    @AssertTrue(
            message = "maximumDelayMs must be greater than "
                    + "or equal to initialDelayMs"
    )
    public boolean isMaximumDelayValid() {
        return maximumDelayMs >= initialDelayMs;
    }
}

//If a remote server is down, hammering it with retries every 1 millisecond makes the outage worse!
//Exponential Backoff increases the waiting time exponentially after each failure:

//What is "Jitter"? (Thundering Herd Prevention)
//Imagine 1,000 workers all fail at 12:00:00 because the database flickered. If all 1,000 workers
// wait exactly 2.0 seconds, at 12:00:02 all 1,000 workers will hit the database at the exact same
// millisecond, crashing it again! (This is called the Thundering Herd Problem).