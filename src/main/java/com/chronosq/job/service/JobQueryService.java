package com.chronosq.job.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.chronosq.execution.JobExecution;
import com.chronosq.execution.JobExecutionRepository;
import com.chronosq.job.domain.Job;
import com.chronosq.job.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
//This class handles read-only queries for jobs and their execution history.
//CQRS stands for Command Query Responsibility Segregation, an architectural pattern that separates read and write operations into distinct models and interfaces.
public class JobQueryService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;


    //Setting readOnly = true tells Spring and JDBC that no data will be modified.
    @Transactional(readOnly = true)
    public Job getJob(UUID jobId) {

        Objects.requireNonNull(
                jobId,
                "jobId must not be null"
        );

        return jobRepository
                .findById(jobId)
                .orElseThrow(
                        () -> new JobNotFoundException(
                                jobId
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<JobExecution> getExecutions(
            UUID jobId
    ) {

        Objects.requireNonNull(
                jobId,
                "jobId must not be null"
        );

        if (jobRepository.findById(jobId)
                .isEmpty()) {

            throw new JobNotFoundException(
                    jobId
            );
        }

        return jobExecutionRepository
                .findByJobId(jobId);
    }
}