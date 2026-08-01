package com.chronosq.configuration;

import org.springframework.boot.context.properties
        .EnableConfigurationProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation
        .Configuration;

import org.springframework.scheduling.annotation
        .EnableScheduling;

import java.time.Clock;

// Marks this class as a Spring configuration class that provides bean definitions and framework setup.
// Registers SchedulerProperties into the Spring application context.
//Without @EnableConfigurationProperties, Spring wouldn't know to process the
// @ConfigurationProperties(prefix = "chronosq.scheduler") on the SchedulerProperties record.
@Configuration(proxyBeanMethods = false)

// Activates Spring’s background task scheduling engine.
//It enables support for annotations like @Scheduled (e.g. @Scheduled(fixedDelay = 1000)),
// setting up a background thread pool to run recurring tasks, pollers, and heartbeats.
@EnableScheduling

// Registers SchedulerProperties into the Spring application context.
// Without @EnableConfigurationProperties, Spring wouldn't know to process the
// @ConfigurationProperties(prefix = "chronosq.scheduler") on the SchedulerProperties record.
@EnableConfigurationProperties(
        SchedulerProperties.class
)

//This class activates and hooks up Spring's background scheduling capabilities and
// registers your SchedulerProperties configuration bean.
public class SchedulingConfiguration {

    @Bean
//In Java 8+, java.time.Clock is an abstract representation of a clock that supplies the current date and time in a specific timezone (in this case, UTC).
  public Clock chronosqClock() {
        return Clock.systemUTC();
    }

}