package com.chronosq.configuration;

import org.springframework.boot.context.properties
        .EnableConfigurationProperties;

import org.springframework.context.annotation
        .Configuration;
//2

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
        WorkerProperties.class
)

//WorkerConfiguration registers WorkerProperties into Spring's application context so it can be injected anywhere in your worker module
public class WorkerConfiguration {
}