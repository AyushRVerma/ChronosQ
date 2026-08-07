package com.chronosq.metrics;

import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;


//this time it's for checking the Health of the application, not just static info.
@Component
public class ExecutionPoolHealthIndicator
        implements HealthIndicator {

    private final ThreadPoolTaskExecutor taskExecutor;

    public ExecutionPoolHealthIndicator(
            @Qualifier("jobExecutionTaskExecutor")
            ThreadPoolTaskExecutor taskExecutor
    ) {
        this.taskExecutor = Objects.requireNonNull(
                taskExecutor,
                "taskExecutor must not be null"
        );
    }

    @Override
    public Health health() {
        ThreadPoolExecutor threadPool =
                taskExecutor.getThreadPoolExecutor();

        if (threadPool.isShutdown()
                || threadPool.isTerminated()) {

            return Health.down()
                    .withDetail(
                            "reason",
                            "Execution thread pool is stopped"
                    )
                    .build();
        }

        return Health.up()
                .withDetail(
                        "activeThreads",
                        threadPool.getActiveCount()
                )
                .withDetail(
                        "currentPoolSize",
                        threadPool.getPoolSize()
                )
                .withDetail(
                        "maximumPoolSize",
                        threadPool.getMaximumPoolSize()
                )
                .withDetail(
                        "queuedJobs",
                        threadPool.getQueue().size()
                )
                .withDetail(
                        "remainingQueueCapacity",
                        threadPool
                                .getQueue()
                                .remainingCapacity()
                )
                .withDetail(
                        "completedTasks",
                        threadPool.getCompletedTaskCount()
                )
                .build();
    }
}

//By default, Spring Boot checks basic things like: "Is the database connected?" and "Is there free disk space?"
//
//But ChronosQ relies heavily on its internal Thread Pool (the group of worker threads executing jobs). If that thread pool crashes, the app might technically still be connected to the database, but it's completely useless because it can't execute any jobs!
//
//Your ExecutionPoolHealthIndicator is a custom "Doctor" you've added to check the health of that specific thread pool.