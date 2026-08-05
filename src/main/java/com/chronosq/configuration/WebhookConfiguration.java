package com.chronosq.configuration;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookProperties.class)

//This class creates a Spring RestClient bean named "webhookRestClient" configured for safe webhook execution.
public class WebhookConfiguration {

    @Bean(name = "webhookRestClient")

    //RestClient is Spring 6.1+'s modern, synchronous HTTP client for making REST API calls to external servers. Think of it as Postman inside Java code!
    public RestClient webhookRestClient(
            RestClient.Builder restClientBuilder,
            WebhookProperties properties
    ) {
//        disable HTTP redirects (NEVER) for Webhook
//        avoid SSRF
//        sending HTTP network requests over the internet.(httpclient)
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        Duration.ofMillis(
                                properties.connectTimeoutMs()
                        )
                )
                .followRedirects(
                        HttpClient.Redirect.NEVER
                )
                .build();

        //Attaches connection & read timeouts so slow external servers
        //never freeze your ChronosQ worker threads indefinitely!

//        is a bridge class in Spring Framework that wraps Java's HttpClient
//        so it can be plugged into Spring's high-level RestClient.
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(
                        httpClient
                );

        requestFactory.setReadTimeout(
                Duration.ofMillis(
                        properties.readTimeoutMs()
                )
        );

        return restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }
}