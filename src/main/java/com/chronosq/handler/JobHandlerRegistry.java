package com.chronosq.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;
//4
@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlersByJobType;

    // List Injection!
    //Spring automatically scans your entire application for any @Component classes that implement JobHandler.
    //It collects all of them into a List<JobHandler> and passes that list into this constructor automatically!
    //You never have to register handlers manually in a config file. Just add @Component to a new JobHandler class and Spring finds it!

    public JobHandlerRegistry(
            List<JobHandler> handlers
    ) {

        Map<String, JobHandler> registeredHandlers = new LinkedHashMap<>();

        for (JobHandler handler : handlers) {

            Objects.requireNonNull(
                    handler,
                    "handler must not be null"
            );

            String jobType = handler.jobType();

            if (jobType == null
                    || jobType.isBlank()) {

                throw new IllegalArgumentException(
                        """
                        handler jobType must not \
                        be blank
                        """
                );
            }

//            If two developers accidentally write two different handlers for "send-email", Spring Boot fails
//            to start immediately with a clear error rather than unpredictably running one over the other!
            JobHandler previousHandler =  registeredHandlers.putIfAbsent(
                            jobType,
                            handler
                    );

            if (previousHandler != null) {
                throw new IllegalStateException(
                        """
                        Multiple handlers are registered \
                        for job type: 
                        """ + jobType
                );
            }
        }

        //Converts the map into an immutable, unmodifiable map handlersByJobType for thread-safety.
        this.handlersByJobType =
                Map.copyOf(registeredHandlers);
    }

    //Lookup Methods
    // Looks up the handler for a job type.
    // If a worker picks up a job with jobType = "generate-pdf", but no handler exists for "generate-pdf", it throws UnknownJobTypeException.
    public JobHandler getRequiredHandler(String jobType) {

        Objects.requireNonNull(
                jobType,
                "jobType must not be null"
        );


        JobHandler handler = handlersByJobType.get(jobType);

        if (handler == null) {
            throw new UnknownJobTypeException(
                    jobType
            );
        }

        return handler;
    }


   //Returns true if a handler exists for that job type, false otherwise.
    public boolean supports(
            String jobType
    ) {

        if (jobType == null) {
            return false;
        }

        return handlersByJobType.containsKey(
                jobType
        );
    }
}