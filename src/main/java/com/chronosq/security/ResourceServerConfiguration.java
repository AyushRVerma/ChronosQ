package com.chronosq.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.annotation.Order;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.Customizer;

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
 * The Authorization Server from Step 6 creates JWTs.
 * This Resource Server validates those JWTs before
 * allowing access to the ChronosQ REST APIs.
 */
@Configuration(proxyBeanMethods = false)
public class ResourceServerConfiguration {

    /*
     * This is the second Spring Security filter chain.
     *
     * @Order(1) belongs to AuthorizationServerConfiguration.
     * @Order(2) handles the remaining ChronosQ endpoints.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {

        /*
         *
         * ChronosQ uses bearer tokens and does not use a
         * browser login session for its REST APIs.
         *
         * CSRF protection mainly protects browser sessions
         * that authenticate through cookies.
         *
         * The OAuth2 Authorization Server endpoints are
         * handled by the separate @Order(1) filter chain.
         */

        http.csrf(
                AbstractHttpConfigurer::disable
        );

        /*
         * Do not create or store an HTTP login session.
         *
         * Every API request must carry its own bearer token.
         */
        http.sessionManagement(
                session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                )
        );

        /*
         * Define which scope is required for every endpoint.
         *
         * Spring converts:
         *
         * jobs.submit
         *
         * into:
         *
         * SCOPE_jobs.submit
         */
        http.authorizeHttpRequests(
                authorization -> authorization

                        /*
                         * Spring Boot uses /error when rendering
                         * framework-level error responses.
                         */
                        .requestMatchers("/error")
                        .permitAll()

                        /*
                         * Kubernetes, Docker and load balancers
                         * need to check application health without
                         * obtaining an OAuth access token.
                         */
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        )
                        .permitAll()

                        /*
                         * Prometheus metrics may reveal internal
                         * application information, so they require
                         * a dedicated permission.
                         */
                        .requestMatchers(
                                "/actuator/prometheus"
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
                         * POST /api/v1/jobs/{id}/cancel
                         *
                         * The * represents one job UUID.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jobs/*/cancel"
                        )
                        .hasAuthority(
                                "SCOPE_jobs.cancel"
                        )

                        /*
                         * Retry a failed or dead-lettered job.
                         *
                         * Retrying is different from submitting
                         * or cancelling, so it has its own scope.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jobs/*/retry"
                        )
                        .hasAuthority(
                                "SCOPE_jobs.retry"
                        )

                        /*
                         * Read the execution history of a job.
                         *
                         * GET /api/v1/jobs/{id}/executions
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
                         * GET /api/v1/jobs/{id}
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
                         * If we create a new endpoint but forget
                         * to define its security rule, Spring denies
                         * access instead of exposing it accidentally.
                         */
                        .anyRequest()
                        .denyAll()
        );

        /*
         * Enable OAuth2 Bearer Token authentication.
         *
         * Spring's BearerTokenAuthenticationFilter will:
         *
         * 1. Read the Authorization header.
         * 2. Extract the bearer JWT.
         * 3. Send it to our JwtDecoder.
         * 4. Create an authenticated SecurityContext.
         */
        http.oauth2ResourceServer(
                oauth2 -> oauth2.jwt(
                        jwt -> jwt.decoder(jwtDecoder)
                )
        );

        return http.build();
    }


    /*
     * Creates the component that validates incoming JWTs.
     *
     * It verifies:
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
         * Load the same RSA pair used by the embedded
         * Authorization Server.
         *
         * The Resource Server only gives the public key
         * to NimbusJwtDecoder.
         */
        RSAKey rsaKey =
                keyStoreLoader.loadRsaKey();

        /*
         * Create a decoder that trusts only the public key
         * associated with our persistent signing key.
         *
         * Explicitly restricting the algorithm to RS256
         * prevents an attacker from selecting an unexpected
         * signing algorithm.
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
         * Default validators check:
         *
         * - exp: expiration time
         * - nbf: not-before time
         * - iss: expected issuer
         * - JWT type constraints
         */
        OAuth2TokenValidator<Jwt> standardValidators =
                JwtValidators.createDefaultWithIssuer(
                        properties
                                .issuer()
                                .toString()
                );

        /*
         * Verify that the JWT was specifically created
         * for the ChronosQ API.
         */
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtAudienceValidator(
                        properties.audience()
                );

        /*
         * The JWT is accepted only when every validator
         * succeeds.
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