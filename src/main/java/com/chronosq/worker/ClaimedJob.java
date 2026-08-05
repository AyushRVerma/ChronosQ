package com.chronosq.worker;

import java.util.Objects;

import com.chronosq.execution.ExecutionStatus;
import com.chronosq.execution.JobExecution;
import com.chronosq.job.domain.Job;
import com.chronosq.job.domain.JobStatus;

//This class is a Value Object / Record that
// represents a job that has been successfully
// claimed by a worker and is currently executing.
public record ClaimedJob(

        Job job,

        JobExecution execution

) {

    public ClaimedJob {

        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        Objects.requireNonNull(
                execution,
                "execution must not be null"
        );

        if (job.status() != JobStatus.RUNNING) {
            throw new IllegalArgumentException(
                    """
                    claimed job status must \
                    be RUNNING
                    """
            );
        }

        if (execution.status()
                != ExecutionStatus.RUNNING) {

            throw new IllegalArgumentException(
                    """
                    claimed execution status must \
                    be RUNNING
                    """
            );
        }

        if (!job.id().equals(
                execution.jobId()
        )) {
            throw new IllegalArgumentException(
                    """
                    execution must belong to \
                    the claimed job
                    """
            );
        }

        //Ensures job.lockedBy() (the worker holding the DB lock on the job)
        // matches execution.workerId() (the worker recorded on the execution log).
        if (!Objects.equals(
                job.lockedBy(),
                execution.workerId()
        )) {
            throw new IllegalArgumentException(
                    """
                    job worker and execution \
                    worker must match
                    """
            );
        }

        if (job.attemptCount()
                != execution.attemptNumber()) {

            throw new IllegalArgumentException(
                    """
                    job attempt and execution \
                    attempt must match
                    """
            );
        }
    }
}
// when a worker claims a job from the database, it needs to return two things to the execution engine:
//
//The updated Job record
//The new JobExecution audit record