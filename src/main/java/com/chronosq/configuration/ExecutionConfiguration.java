package com.chronosq.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExecutionProperties.class)

// This class sets up the Multi-Threaded Execution Engine for running claimed jobs in parallel!
public class ExecutionConfiguration {


    //It creates a Spring-managed ThreadPoolTaskExecutor bean configured with the values from ExecutionProperties.
    // It is a manager that controls a pool of reusable background worker threads.
//    ThreadPoolTaskExecutor is an efficient thread manager that keeps a fixed set of background worker threads alive,
//    queuing up tasks and processing them concurrently without overwhelming your server's memory or CPU.

    @Bean(name = "jobExecutionTaskExecutor")
    public ThreadPoolTaskExecutor jobExecutionTaskExecutor(
            ExecutionProperties executionProperties
    ) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

//        The minimum number of threads that are always kept alive, even if they are idle and doing nothing.
        executor.setCorePoolSize(
                executionProperties.threadCount()
        );

//        The maximum upper limit of threads the pool is allowed to expand to under heavy load.
        executor.setMaxPoolSize(
                executionProperties.threadCount()
        );

//        ets the capacity of the in-memory queue (e.g. 1000).
        executor.setQueueCapacity(
                executionProperties.queueCapacity()
        );

        //names all background threads in the pool (chronosq-job-1, chronosq-job-2, etc.).
        executor.setThreadNamePrefix(
                "chronosq-job-"
        );

        executor.setWaitForTasksToCompleteOnShutdown(
                true
        );

        executor.setAwaitTerminationSeconds(30);

        return executor;
    }
}