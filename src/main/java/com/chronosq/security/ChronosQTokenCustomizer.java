package com.chronosq.security;

import java.util.List;
import java.util.Objects;

import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import org.springframework.stereotype.Component;


/*
 * Adds ChronosQ-specific information to JWT access tokens
 * before the Authorization Server signs them.
 *
 * This class currently adds the "aud" claim.
 *
 * Example:
 *
 * {
 *     "iss": "http://localhost:8080",
 *     "sub": "chronosq-postman-client",
 *     "aud": ["chronosq-api"],
 *     "scope": ["jobs.submit", "jobs.read"],
 *     "exp": 1787780000
 * }
 */
@Component
public final class ChronosQTokenCustomizer
        implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final JwtKeyStoreProperties properties;


    /*
     * Spring injects the validated JWT configuration
     * properties through this constructor.
     */
    public ChronosQTokenCustomizer(
            JwtKeyStoreProperties properties
    ) {
        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );
    }


    /*
     * Spring calls this method immediately before it
     * signs and creates a JWT.
     */

    // JwtEncodingContext in Spring Authorization Server holds the data and parameters needed when creating a JSON Web Token (JWT)
    @Override
    public void customize(
            JwtEncodingContext context
    ) {
        Objects.requireNonNull(
                context,
                "context must not be null"
        );

        /*
         * A token customizer can be called for different
         * types of JWTs.
         *
         * We only want to modify OAuth2 access tokens.
         * We do not want to accidentally modify another
         * token type in the future.
         */
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(
                context.getTokenType()
        )) {
            return;
        }

        /*
         * Add the audience claim.
         *
         * "aud" answers:
         *
         * "Which API is this token allowed to access?"
         *
         * In our project:
         *
         * aud = ["chronosq-api"]
         */
        context.getClaims()
                .audience(
                        List.of(
                                properties.audience()
                        )
                );
    }
}