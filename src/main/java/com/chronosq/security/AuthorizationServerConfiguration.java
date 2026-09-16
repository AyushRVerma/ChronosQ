package com.chronosq.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.config.annotation.web.configurers
        .oauth2.server.authorization
        .OAuth2AuthorizationServerConfigurer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.core.annotation.Order;

import org.springframework.security.config.Customizer;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import org.springframework.security.web.SecurityFilterChain;


/*
 * Configures the OAuth2 Authorization Server inside ChronosQ.
 *
 * Its responsibilities are:
 *
 * 1. Register trusted machine clients.
 * 2. Authenticate clients at the token endpoint.
 * 3. Issue signed JWT access tokens.
 * 4. Publish the public RSA key through the JWK endpoint.
 * 5. Configure the token issuer and token lifetime.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        OAuthClientProperties.class,
        JwtKeyStoreProperties.class
})
public class AuthorizationServerConfiguration {

    /*
     * This is the first Spring Security filter chain.
     *
     * @Order(1) gives the OAuth2 protocol endpoints
     * higher priority than the normal ChronosQ API
     * security chain that we will create later.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        /*
         * Create the Spring Authorization Server configurer.
         */
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        /*
         * Restrict this filter chain to OAuth2 Authorization
         * Server endpoints only.
         *
         * Examples:
         *
         * /oauth2/token
         * /oauth2/jwks
         * /oauth2/introspect
         * /oauth2/revoke
         * /.well-known/oauth-authorization-server
         */
        http.securityMatcher(
                authorizationServerConfigurer
                        .getEndpointsMatcher()
        );

        /*
         * Install the Authorization Server filters.
         */
        http.with(
                authorizationServerConfigurer,
                Customizer.withDefaults()
        );

        return http.build();
    }


    /*
     * PasswordEncoder protects the configured client secret.
     *
     * PasswordEncoderFactories creates a delegating encoder.
     * Its default encoding algorithm is BCrypt.
     *
     * Spring stores a value similar to:
     *
     * {bcrypt}$2a$10$...
     *
     * The original client secret is not stored directly
     * inside the RegisteredClient object.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {

        return PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
    }


    /*
     * Registers the machine client allowed to request
     * access tokens from ChronosQ.
     *
     * For now, this client is kept in memory.
     * Restarting the application recreates it from
     * configuration properties.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            OAuthClientProperties properties,
            PasswordEncoder passwordEncoder
    ) {

        /*
         * Create a deterministic internal registration ID.
         *
         * This is different from the public client ID.
         * Spring uses it internally to identify the
         * RegisteredClient record.
         */
        String registrationId =
                UUID.nameUUIDFromBytes(
                        (
                                "chronosq-oauth-client:"
                                        + properties.clientId()
                        ).getBytes(StandardCharsets.UTF_8)
                ).toString();

        RegisteredClient.Builder clientBuilder =
                RegisteredClient
                        .withId(registrationId)

                        /*
                         * Public identifier sent by the client.
                         */
                        .clientId(
                                properties.clientId()
                        )

                        /*
                         * Store an encoded version of the
                         * configured client secret.
                         */
                        .clientSecret(
                                passwordEncoder.encode(
                                        properties.clientSecret()
                                )
                        )

                        /*
                         * The client sends its credentials using
                         * HTTP Basic Authentication:
                         *
                         * Authorization:
                         * Basic base64(clientId:clientSecret)
                         */
                        .clientAuthenticationMethod(
                                ClientAuthenticationMethod
                                        .CLIENT_SECRET_BASIC
                        )

                        /*
                         * client_credentials is designed for
                         * application-to-application authentication.
                         *
                         * No human login page is required.
                         */
                        .authorizationGrantType(
                                AuthorizationGrantType
                                        .CLIENT_CREDENTIALS
                        )

                        /*
                         * Apply our configured access-token lifetime.
                         *
                         * Client Credentials does not require
                         * a refresh token. When the token expires,
                         * the client requests another access token.
                         */
                        .tokenSettings(
                                TokenSettings.builder()
                                        .accessTokenTimeToLive(
                                                properties
                                                        .accessTokenTimeToLive()
                                        )
                                        .build()
                        );

        /*
         * Give the client only the configured permissions.
         *
         * Sorting is not required for security, but it
         * makes the registration deterministic and easier
         * to inspect while debugging.
         */
        properties.scopes()
                .stream()
                .sorted()
                .forEach(clientBuilder::scope);

        RegisteredClient registeredClient =
                clientBuilder.build();

        return new InMemoryRegisteredClientRepository(
                registeredClient
        );
    }


    /*
     * Converts the persistent RSA key into a JWKSource.
     *
     * Spring Authorization Server uses this bean:
     *
     * - private key -> sign JWT tokens
     * - public key  -> publish through /oauth2/jwks
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(
            JwtKeyStoreLoader keyStoreLoader
    ) {

        RSAKey rsaKey =
                keyStoreLoader.loadRsaKey();

        JWKSet jwkSet =
                new JWKSet(rsaKey);

        /*
         * Spring supplies a selector containing the requested
         * key ID and algorithm. The selector chooses a matching
         * key from our JWK set.
         */
        return (
                jwkSelector,
                securityContext
        ) -> jwkSelector.select(jwkSet);
    }


    /*
     * Defines the identity of our Authorization Server.
     *
     * The issuer becomes the JWT "iss" claim.
     *
     * Example:
     *
     * "iss": "http://localhost:8080"
     *
     * Later, ChronosQ's Resource Server will reject tokens
     * whose issuer does not match this value.
     */
    @Bean
    public AuthorizationServerSettings
    authorizationServerSettings(
            JwtKeyStoreProperties properties
    ) {

        return AuthorizationServerSettings.builder()
                .issuer(
                        properties
                                .issuer()
                                .toString()
                )
                .build();
    }
}