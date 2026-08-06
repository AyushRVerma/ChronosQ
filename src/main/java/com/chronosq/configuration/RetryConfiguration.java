package com.chronosq.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

//Marks this class as a Spring configuration bean.
//proxyBeanMethods = false disables CGLIB proxy generation to optimize memory usage and application startup speed.
@Configuration(proxyBeanMethods = false)

@EnableConfigurationProperties(RetryProperties.class)
public class RetryConfiguration {
}