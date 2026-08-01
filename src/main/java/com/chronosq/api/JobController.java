package com.chronosq.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;
import com.chronosq.job.service.JobLifecycleService;
import com.chronosq.job.service.JobQueryService;
import com.chronosq.job.service.JobSubmissionService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.support
        .ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor

//JobController exposes HTTP endpoints for managing jobs over REST/JSON.
// It delegates business operations to services (JobSubmissionService, JobQueryService, JobLifecycleService)
// and uses JobApiMapper to transform domain objects into HTTP responses.
public class JobController {

    private final JobSubmissionService
            jobSubmissionService;

    private final JobQueryService
            jobQueryService;

    private final JobLifecycleService
            jobLifecycleService;

    private final JobApiMapper jobApiMapper;

    // @Valid triggers Spring's Bean Validation on SubmitJobRequest before execution.


    @PostMapping
    public ResponseEntity<JobResponse> submitJob(
            @Valid
            @RequestBody
            SubmitJobRequest request
    ) {

        Job job =
                jobSubmissionService.submit(request);
     // According to REST Standard Specifications (RFC 9110), when you create a new resource via HTTP POST,
        // you should return a Location response header telling the client where to find the newly created resource.
        //
        //Let's see how the URI is dynamically constructed:
        //
        //fromCurrentRequest(): Gets the current request URL (e.g., http://localhost:8080/api/v1/jobs).
        //.path("/{jobId}"): Appends /{jobId} template to the URL (http://localhost:8080/api/v1/jobs/{jobId}).
        //.buildAndExpand(job.id()): Replaces {jobId} with the actual UUID generated for this job (e.g. a3f9-1234-5678).
        //.toUri(): Converts the result into a URI object (http://localhost:8080/api/v1/jobs/a3f9-1234-5678).

        URI location =
                ServletUriComponentsBuilder
                        .fromCurrentRequest()
                        .path("/{jobId}")
                        .buildAndExpand(job.id())
                        .toUri();

        return ResponseEntity
                .created(location)
                .body(
                        jobApiMapper.toResponse(job)
                );
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(
            @PathVariable("jobId")
            UUID jobId
    ) {

        Job job =
                jobQueryService.getJob(jobId);

        return ResponseEntity.ok(
                jobApiMapper.toResponse(job)
        );
    }

    @GetMapping("/{jobId}/executions")
    public ResponseEntity<
            List<JobExecutionResponse>
            > getJobExecutions(

            @PathVariable("jobId")
            UUID jobId
    ) {

        var executions =
                jobQueryService.getExecutions(
                        jobId
                );

        return ResponseEntity.ok(
                jobApiMapper.toExecutionResponses(
                        executions
                )
        );
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<JobResponse> cancelJob(
            @PathVariable("jobId")
            UUID jobId
    ) {

        Job cancelledJob =
                jobLifecycleService.transitionTo(
                        jobId,
                        JobStatus.CANCELLED
                );

        return ResponseEntity.ok(
                jobApiMapper.toResponse(
                        cancelledJob
                )
        );
    }
}