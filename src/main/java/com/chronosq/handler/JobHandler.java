package com.chronosq.handler;

import com.chronosq.job.domain.Job;

public interface JobHandler {

    String jobType();

    void execute(Job job)
            throws Exception;
}

//Defines the contract for executing actual job work.
//Requires implementing classes to provide two things:
//jobType() ➔ The name of the job type it handles (e.g. "send-email").
//execute(Job job) ➔ The actual business code to run (e.g. sending an email).