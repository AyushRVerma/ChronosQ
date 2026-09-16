package com.chronosq.security;

import java.time.Duration;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


// An OAuth client is an application allowed to request access tokens.
// OAuthClientProperties defines:
//- Which machine client is registered
//- Its private credential
//- Which actions it may request
//- How long its access tokens remain valid
//It does not issue a JWT itself.

@Validated
@ConfigurationProperties(prefix = "chronosq.security.oauth-client")

public record OAuthClientProperties(

        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,99}$",
                message = """
                        OAuth client ID must contain 3–100 \
                        letters, numbers, dots, underscores or hyphens
                        """
        )
        String clientId,

        @NotBlank
        @Size(
                min = 32,
                max = 200,
                message = """
                        OAuth client secret must contain \
                        between 32 and 200 characters
                        """
        )
        String clientSecret,

        @NotEmpty
        Set<
                @NotBlank
                @Pattern(
                        regexp =
                                "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9-]*)+$",
                        message = """
                                OAuth scopes must use names \
                                such as jobs.submit
                                """
                )
                        String
                > scopes,

        @NotNull
        Duration accessTokenTimeToLive

) {

    private static final Duration MINIMUM_TOKEN_LIFETIME =
            Duration.ofMinutes(1);

    private static final Duration MAXIMUM_TOKEN_LIFETIME =
            Duration.ofHours(1);


    //A scope is a permission written inside an access token.
    //It tells ChronosQ what the authenticated client is allowed to do.

    public OAuthClientProperties {
        if (scopes != null) {

            //A Set cannot contain duplicate entries.
            scopes = Set.copyOf(scopes);
        }
    }

    @AssertTrue(
            message = """
                    Access-token lifetime must be between \
                    1 minute and 1 hour
                    """
    )
    public boolean isAccessTokenTimeToLiveValid() {

        if (accessTokenTimeToLive == null) {
            return true;
        }

        return accessTokenTimeToLive.compareTo(
                MINIMUM_TOKEN_LIFETIME
        ) >= 0
                && accessTokenTimeToLive.compareTo(
                MAXIMUM_TOKEN_LIFETIME
        ) <= 0;
    }

    @Override
    public String toString() {
        return """
                OAuthClientProperties[
                    clientId=%s,
                    clientSecret=<redacted>,
                    scopes=%s,
                    accessTokenTimeToLive=%s
                ]
                """.formatted(
                clientId,
                scopes,
                accessTokenTimeToLive
        );
    }
}