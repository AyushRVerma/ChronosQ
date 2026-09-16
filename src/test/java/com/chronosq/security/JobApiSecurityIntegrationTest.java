package com.chronosq.security;

import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.boot.flyway.autoconfigure
        .FlywayAutoConfiguration;

import org.springframework.boot.jdbc.autoconfigure
        .DataSourceAutoConfiguration;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

import java.time.Clock;

import java.util.Map;

import com.nimbusds.jose.jwk.RSAKey;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import org.springframework.boot.context.properties
        .EnableConfigurationProperties;

import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.webmvc.test.autoconfigure
        .AutoConfigureMockMvc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.authority
        .SimpleGrantedAuthority;

import org.springframework.test.web.servlet.MockMvc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


/*
 * Tests scope-based authorization independently from
 * the ChronosQ database and job-processing services.
 */
@SpringBootTest(
        classes = {
                JobApiSecurityIntegrationTest
                        .TestApplication.class,
                TestJwtKeyConfiguration.class
        },
        properties = {
                """
                chronosq.security.jwt.\
                key-store-location=classpath:unused.p12
                """,

                """
                chronosq.security.jwt.\
                key-store-password=test-password-123
                """,

                """
                chronosq.security.jwt.\
                key-alias=test-jwt-key
                """,

                """
                chronosq.security.jwt.\
                private-key-password=test-password-123
                """,

                """
                chronosq.security.jwt.\
                issuer=http://localhost:8080
                """,

                """
                chronosq.security.jwt.\
                audience=chronosq-api
                """
        }
)
@AutoConfigureMockMvc
class JobApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;


    /*
     * Small test application containing:
     *
     * - Resource Server security
     * - JSON security error handlers
     * - a fake job controller
     *
     * The fake controller allows security rules to be
     * tested without inserting jobs into PostgreSQL.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(
            exclude = {
                    DataSourceAutoConfiguration.class,
                    FlywayAutoConfiguration.class
            }
    )
    @EnableConfigurationProperties(
            JwtKeyStoreProperties.class
    )
    @Import({
            ResourceServerConfiguration.class,
            JsonAuthenticationEntryPoint.class,
            JsonAccessDeniedHandler.class,
            TestJobController.class
    })
    static class TestApplication {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }


    @Test
    void shouldReturn401WhenTokenIsMissing()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/jobs")
                                .contentType(
                                        "application/json"
                                )
                                .content(
                                        """
                                        {
                                          "message": "hello"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "AUTHENTICATION_REQUIRED"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/jobs")
                );
    }


    @Test
    void shouldReturn403WhenSubmitScopeIsMissing()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/jobs")

                                /*
                                 * Create an authenticated test JWT
                                 * containing only jobs.read.
                                 */
                                .with(
                                        jwt().authorities(
                                                new SimpleGrantedAuthority(
                                                        "SCOPE_jobs.read"
                                                )
                                        )
                                )

                                .contentType(
                                        "application/json"
                                )

                                .content(
                                        """
                                        {
                                          "message": "hello"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("ACCESS_DENIED")
                );
    }


    @Test
    void shouldAllowJobSubmissionWithSubmitScope()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/jobs")
                                .with(
                                        jwt().authorities(
                                                new SimpleGrantedAuthority(
                                                        "SCOPE_jobs.submit"
                                                )
                                        )
                                )
                                .contentType(
                                        "application/json"
                                )
                                .content(
                                        """
                                        {
                                          "message": "hello"
                                        }
                                        """
                                )
                )
                .andExpect(
                        status().isAccepted()
                );
    }


    @Test
    void shouldAllowReadingJobWithReadScope()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{id}",
                                "8e688700-606a-4f6d-aeed-2fb41d252263"
                        )
                                .with(
                                        jwt().authorities(
                                                new SimpleGrantedAuthority(
                                                        "SCOPE_jobs.read"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }


    @Test
    void shouldRejectReadRequestWithSubmitScope()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/jobs/{id}",
                                "8e688700-606a-4f6d-aeed-2fb41d252263"
                        )
                                .with(
                                        jwt().authorities(
                                                new SimpleGrantedAuthority(
                                                        "SCOPE_jobs.submit"
                                                )
                                        )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }


    /*
     * Test-only controller.
     *
     * Its routes match the real ChronosQ routes, but
     * it contains no database or scheduling logic.
     */
    @RestController
    static class TestJobController {

        @PostMapping("/api/v1/jobs")
        ResponseEntity<Map<String, String>> submitJob(
                @RequestBody Map<String, Object> request
        ) {

            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(
                            Map.of(
                                    "status",
                                    "accepted"
                            )
                    );
        }


        @GetMapping("/api/v1/jobs/{jobId}")
        Map<String, String> getJob(
                @PathVariable String jobId
        ) {

            return Map.of(
                    "id",
                    jobId
            );
        }
    }
}