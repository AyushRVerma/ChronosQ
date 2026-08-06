package com.chronosq.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)

//Tells Spring to instantiate HeartbeatProperties populated with YAML data and add it to the Spring context.
@EnableConfigurationProperties(HeartbeatProperties.class)
public class HeartbeatConfiguration {
}