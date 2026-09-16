package com.chronosq.security;

import java.net.URI;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

//A keystore is a password-protected file containing cryptographic keys.
// ChronosQ’s keystore will contain:
// RSA private key
// RSA public key/certificate

// JwtKeyStoreProperties describes:
//- Where the RSA keys are stored
//- How the keystore and private key are unlocked
//- Who issues tokens
//- Which API should accept them
//It does not load or use the keys yet.

@Validated
@ConfigurationProperties(
        prefix = "chronosq.security.jwt"
)
public record JwtKeyStoreProperties(

        // Tells ChronosQ where the keystore file exists.
        @NotBlank
        String keyStoreLocation,

        @NotBlank
        @Size(
                min = 12,
                max = 200,
                message = """
                        JWT keystore password must contain \
                        between 12 and 200 characters
                        """
        )

        // Unlocks the entire PKCS12 file:
        // PKCS #12 is a secure file format used to bundle a private key, its public X.509 certificate,
        // and any intermediate trust certificates into a single, password-protected archive.
        // These files commonly use .p12 or .pfx extensions and are widely used for installing SSL/TLS certificates on servers or user devices
        String keyStorePassword,



        // A keystore can contain several keys. The alias identifies which one ChronosQ should use:
        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z0-9._-]{1,100}$",
                message = "JWT key alias contains invalid characters"
        )
        String keyAlias,



        // Unlocks the selected private key.
        @NotBlank
        @Size(
                min = 12,
                max = 200,
                message = """
                        JWT private-key password must contain \
                        between 12 and 200 characters
                        """
        )
        String privateKeyPassword,

        // Identifies who created the JWT:
        // During validation, ChronosQ checks that the token was created by its trusted issuer.
        //An attacker must not be able to create a token containing another issuer and have it accepted merely because the JSON looks correct—the signature must also match.
        @NotNull
        URI issuer,

        // Identifies the intended receiver of the token:
        // This prevents a JWT created for another application from being accepted by ChronosQ.
        @NotBlank
        @Size(max = 200)
        String audience

) {

    @AssertTrue(message = """
                    JWT issuer must be an absolute \
                    HTTP or HTTPS URL
                    """)
    public boolean isIssuerValid() {

        if (issuer == null) {
            return true;
        }

        String scheme = issuer.getScheme();

        boolean supportedScheme =
                "http".equalsIgnoreCase(scheme)
                        || "https".equalsIgnoreCase(scheme);

        return issuer.isAbsolute()
                && supportedScheme
                && issuer.getHost() != null
                && !issuer.getHost().isBlank()
                && issuer.getFragment() == null;
    }

    @Override
    public String toString() {
        return """
                JwtKeyStoreProperties[
                    keyStoreLocation=%s,
                    keyStorePassword=<redacted>,
                    keyAlias=%s,
                    privateKeyPassword=<redacted>,
                    issuer=%s,
                    audience=%s
                ]
                """.formatted(
                keyStoreLocation,
                keyAlias,
                issuer,
                audience
        );
    }
}