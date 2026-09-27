package com.chronosq.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.chronosq.job.domain.MissedExecutionPolicy;
import com.chronosq.job.domain.ScheduleType;

import tools.jackson.databind.ObjectMapper;

class SubmitJobRequestCronTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptValidCronConfiguration() throws Exception {
        SubmitJobRequest request = cronRequest(
                "0 0 9 * * MON-FRI",
                "Asia/Kolkata",
                MissedExecutionPolicy.SKIP
        );

        assertThat(request.isScheduleConfigurationValid()).isTrue();
    }

    @Test
    void shouldRejectInvalidExpressionAndTimeZone() throws Exception {
        assertThat(cronRequest(
                "not-a-cron",
                "Mars/Olympus",
                MissedExecutionPolicy.CATCH_UP_ALL
        ).isScheduleConfigurationValid()).isFalse();
    }

    private SubmitJobRequest cronRequest(
            String expression,
            String timeZone,
            MissedExecutionPolicy policy
    ) throws Exception {
        return new SubmitJobRequest(
                "default",
                "PRINT_MESSAGE",
                objectMapper.readTree("{\"message\":\"hello\"}"),
                0,
                null,
                ScheduleType.CRON,
                null,
                expression,
                timeZone,
                policy,
                3,
                30,
                null
        );
    }
}
