package com.chronosq.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.web.SecurityFilterChain;


/*
 * Configures ChronosQ as an OAuth2 Resource Server.
 *
 * The Authorization Server creates signed JWT access tokens.
 * This Resource Server validates those tokens before allowing
 * clients to access the ChronosQ REST APIs.
 */
@Configuration(proxyBeanMethods = false)
public class ResourceServerConfiguration {

    /*
     * This is the second Spring Security filter chain.
     *
     * @Order(1) handles OAuth2 Authorization Server endpoints.
     * @Order(2) handles the remaining ChronosQ endpoints.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler
    ) throws Exception {

        /*
         * ChronosQ uses JWT bearer tokens instead of
         * browser cookies for authentication.
         *
         * Therefore, traditional browser-based CSRF
         * protection is not required for these APIs.
         */
        http.csrf(
                AbstractHttpConfigurer::disable
        );

        /*
         * ChronosQ does not create HTTP login sessions.
         *
         * Every request must contain its own access token:
         *
         * Authorization: Bearer <access-token>
         */
        http.sessionManagement(
                session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                )
        );

        /*
         * Define the permission required by each endpoint.
         *
         * An OAuth scope such as:
         *
         * jobs.submit
         *
         * becomes the Spring Security authority:
         *
         * SCOPE_jobs.submit
         */
        http.authorizeHttpRequests(
                authorization -> authorization

                        /*
                         * Spring Boot may forward framework-level
                         * errors to this endpoint.
                         */
                        .requestMatchers("/error")
                        .permitAll()

                        /*
                         * Health and application information must
                         * remain accessible to Docker, Kubernetes
                         * and load balancers.
                         */
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        )
                        .permitAll()

                        /*
                         * Prometheus metrics can reveal internal
                         * information, so they require permission.
                         */
                        .requestMatchers(
                                "/actuator/prometheus"
                        )
                        .hasAuthority(
                                "SCOPE_metrics.read"
                        )

                        /*
                         * Read the operational dashboard snapshot.
                         * The response contains aggregate metrics,
                         * worker health and recent execution details.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/dashboard"
                        )
                        .hasAuthority(
                                "SCOPE_metrics.read"
                        )

                        /*
                         * Submit a new background job.
                         *
                         * POST /api/v1/jobs
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jobs"
                        )
                        .hasAuthority(
                                "SCOPE_jobs.submit"
                        )

                        /*
                         * Cancel an existing job.
                         *
                         * POST /api/v1/jobs/{jobId}/cancel
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jobs/*/cancel"
                        )
                        .hasAuthority(
                                "SCOPE_jobs.cancel"
                        )
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jobs/*/requeue"
                        )
                        .hasAuthority("SCOPE_jobs.requeue")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/jobs/*/requeues"
                        )
                        .hasAuthority("SCOPE_jobs.read")
                        /*
                         * Read the execution history of a job.
                         *
                         * GET /api/v1/jobs/{jobId}/executions
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/jobs/*/executions"
                        )
                        .hasAuthority(
                                "SCOPE_jobs.read"
                        )

                        /*
                         * Read one job.
                         *
                         * GET /api/v1/jobs/{jobId}
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/jobs/*"
                        )
                        .hasAuthority(
                                "SCOPE_jobs.read"
                        )

                        /*
                         * Fail closed.
                         *
                         * Any endpoint without an explicit security
                         * rule is denied automatically.
                         */
                        .anyRequest()
                        .denyAll()
        );

        /*
         * Enable OAuth2 bearer-token authentication.
         *
         * Spring Security will:
         *
         * 1. Read the Authorization header.
         * 2. Extract the JWT bearer token.
         * 3. Validate it using JwtDecoder.
         * 4. Create an authenticated SecurityContext.
         */
        http.oauth2ResourceServer(
                oauth2 -> oauth2.jwt(
                        jwt -> jwt.decoder(jwtDecoder)
                )
        );

        /*
         * Connect our custom JSON error handlers.
         *
         * Missing or invalid JWT:
         * JsonAuthenticationEntryPoint returns HTTP 401.
         *
         * Valid JWT without the required scope:
         * JsonAccessDeniedHandler returns HTTP 403.
         */
        http.exceptionHandling(
                exceptions -> exceptions
                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )
                        .accessDeniedHandler(
                                accessDeniedHandler
                        )
        );

        return http.build();
    }


    /*
     * Creates the component responsible for validating
     * incoming JWT access tokens.
     *
     * Validation includes:
     *
     * 1. RSA signature
     * 2. RS256 signing algorithm
     * 3. Expiration time
     * 4. Not-before time
     * 5. Issuer
     * 6. Audience
     */
    @Bean
    public JwtDecoder jwtDecoder(
            JwtKeyStoreLoader keyStoreLoader,
            JwtKeyStoreProperties properties
    ) throws JOSEException {

        /*
         * Load the RSA key pair used by the embedded
         * Authorization Server.
         */
        RSAKey rsaKey =
                keyStoreLoader.loadRsaKey();

        /*
         * Only provide the public key to the decoder.
         *
         * A public key can validate a signature, but it
         * cannot create new valid JWT signatures.
         */
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withPublicKey(
                                rsaKey.toRSAPublicKey()
                        )
                        .signatureAlgorithm(
                                SignatureAlgorithm.RS256
                        )
                        .build();

        /*
         * These validators check:
         *
         * - token expiration
         * - not-before time
         * - expected issuer
         * - other standard JWT constraints
         */
        OAuth2TokenValidator<Jwt> standardValidators =
                JwtValidators.createDefaultWithIssuer(
                        properties
                                .issuer()
                                .toString()
                );

        /*
         * Confirm that the token was specifically created
         * for the ChronosQ API.
         */
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtAudienceValidator(
                        properties.audience()
                );

        /*
         * The JWT is accepted only when all validators pass.
         */
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        standardValidators,
                        audienceValidator
                )
        );

        return decoder;
    }
}
