package com.chronosq.handler;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.chronosq.job.domain.Job;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component

//PrintMessageJobHandler handles jobs of type "PRINT_MESSAGE".
//When a worker picks up a "PRINT_MESSAGE" job, it runs this class's execute(job) method

public class PrintMessageJobHandler implements JobHandler {

    public static final String JOB_TYPE = "PRINT_MESSAGE";

    private final ObjectMapper objectMapper;

    public PrintMessageJobHandler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void execute(Job job) throws Exception {
        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        if (!JOB_TYPE.equals(job.jobType())) {
            throw new IllegalArgumentException(
                    "PrintMessageJobHandler cannot process job type: "
                            + job.jobType()
            );
        }

        //Deserialization & Strong-Typing: Parse JSON payload string into PrintMessagePayload record
        PrintMessagePayload payload = objectMapper.readValue(job.payload(),
                        PrintMessagePayload.class
                );

        log.info(
                "PRINT_MESSAGE job completed: jobId={}, message={}",
                job.id(),
                payload.message()
        );
    }
}