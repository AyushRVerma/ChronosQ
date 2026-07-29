package com.chronosq.job.domain;

import javax.print.attribute.standard.JobState;
import java.util.Objects;

public final class JobStateMachine {

    private JobStateMachine(){
        // Prevent creating objects because this class only has static methods.
    }

    public static boolean canTransition(
            JobStatus currentStatus, JobStatus newStatus) {
             Objects.requireNonNull(currentStatus, "Current job status must not be null");
             Objects.requireNonNull(newStatus, "New job status must not be null");

        return switch(currentStatus){
            case SCHEDULED, RETRY_WAIT -> newStatus == JobStatus.READY ||  newStatus == JobStatus.CANCELLED;

            case READY -> newStatus == JobStatus.RUNNING ||  newStatus == JobStatus.CANCELLED;

            case RUNNING -> newStatus == JobStatus.SUCCEEDED ||  newStatus == JobStatus.RETRY_WAIT || newStatus == JobStatus.DEAD_LETTERED;

            case SUCCEEDED , DEAD_LETTERED , CANCELLED ->  false;
        };
    }

    public static void validateTransition(
            JobStatus currentStatus, JobStatus newStatus) {

        if(!canTransition(currentStatus, newStatus)){
            throw new IllegalStateException("Invalid job status transition: " +currentStatus+" -> "+newStatus);
        }
    }
}
