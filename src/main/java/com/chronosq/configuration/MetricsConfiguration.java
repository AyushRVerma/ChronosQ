package com.chronosq.configuration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
public class MetricsConfiguration {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return meterRegistry -> meterRegistry.config()
                .commonTags(
                        "application",
                        "chronosq"
                );
    }
}

//By creating this @Bean, Spring intercepts the MeterRegistry right as the application starts.
// It applies a Common Tag (application=chronosq) to every single metric that the application generates—whether
// you wrote the metric yourself or Spring generated it automatically under the hood