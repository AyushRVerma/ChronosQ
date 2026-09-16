package com.chronosq.security;

import java.io.IOException;
import java.io.InputStream;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;

import java.security.cert.Certificate;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import java.util.Arrays;

import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;


/*
 * Loads the persistent RSA signing key from the PKCS12 keystore created
 *
 * The private key will be used by the Authorization Server to sign JWT access tokens.
 *
 * The public key will be used by the Resource Server to verify those JWT signatures.
 */
@Component
public final class JwtKeyStoreLoader {

    private static final String KEYSTORE_TYPE = "PKCS12";

    private final ResourceLoader resourceLoader;

    private final JwtKeyStoreProperties properties;


    /*
     * Spring calls this constructor and injects:
     *
     * 1. ResourceLoader:
     *    Used to find files using locations such as:
     *    file:./secrets/chronosq-jwt.p12
     *
     * 2. JwtKeyStoreProperties:
     *    Contains the configured keystore location,
     *    passwords and alias.
     */
    public JwtKeyStoreLoader(
            ResourceLoader resourceLoader,
            JwtKeyStoreProperties properties
    ) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }


    /*
     * Opens the PKCS12 file, extracts its private/public
     * RSA keys and converts them into Nimbus RSAKey format.
     *
     * Spring Authorization Server works with the Nimbus
     * JWK classes when signing and publishing public keys.
     */
    public RSAKey loadRsaKey() {

        /*
         * Password APIs in Java's KeyStore use char[].
         *
         * A char[] can be cleared after use, whereas a String
         * cannot be manually erased from memory.
         */
        char[] keyStorePassword =
                properties
                        .keyStorePassword()
                        .toCharArray();

        char[] privateKeyPassword =
                properties
                        .privateKeyPassword()
                        .toCharArray();

        try {
            Resource keyStoreResource = loadKeyStoreResource();

            KeyStore keyStore =
                    loadKeyStore(
                            keyStoreResource,
                            keyStorePassword
                    );

            RSAPrivateKey privateKey =
                    loadPrivateKey(
                            keyStore,
                            privateKeyPassword
                    );

            RSAPublicKey publicKey =
                    loadPublicKey(keyStore);

            /*
             * Combine the two Java RSA keys into the
             * RSAKey format required by Nimbus JOSE.
             *
             * The key ID becomes the JWT header's "kid".
             * It tells verifiers which public key should
             * be used to verify a particular JWT.
             */
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(properties.keyAlias())
                    .build();

        } catch (
                IOException
                | GeneralSecurityException exception
        ) {
            /*
             * Do not put either password in this message.
             *
             * If loading fails, ChronosQ should not start
             * because it would be unable to sign tokens.
             */
            throw new IllegalStateException(
                    """
                    Could not load the ChronosQ JWT signing key. \
                    Check the keystore location, passwords and alias.
                    """,
                    exception
            );

        } finally {

            //  Remove password characters from these temporary arrays after the keystore operation finishes.

            Arrays.fill(
                    keyStorePassword,
                    '\0'
            );

            Arrays.fill(
                    privateKeyPassword,
                    '\0'
            );
        }
    }


    /*
     * Converts the configured location into a Spring Resource.
     *
     * Example configured location:
     * file:./secrets/chronosq-jwt.p12
     */
    private Resource loadKeyStoreResource() {

        Resource resource = resourceLoader.getResource(properties.keyStoreLocation());

        if (!resource.exists()) {
            throw new IllegalStateException(
                    """
                    JWT keystore does not exist: %s
                    """.formatted(
                            resource.getDescription()
                    )
            );
        }

        if (!resource.isReadable()) {
            throw new IllegalStateException(
                    """
                    JWT keystore is not readable: %s
                    """.formatted(
                            resource.getDescription()
                    )
            );
        }

        return resource;
    }


    /*
     * Creates an empty PKCS12 KeyStore object and then
     * fills it by reading the .p12 file.
     */
    private KeyStore loadKeyStore(
            Resource resource,
            char[] keyStorePassword
    ) throws IOException, GeneralSecurityException {

        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);

        /*
         * try-with-resources automatically closes the
         * file input stream after reading the keystore.
         */
        try (
                InputStream inputStream =
                        resource.getInputStream()
        ) {
            keyStore.load(
                    inputStream,
                    keyStorePassword
            );
        }

        return keyStore;
    }


    /*
     * Finds the key using the alias and private-key password.
     *
     * It must be an RSA private key because our JWT signing
     * configuration uses an RSA signing algorithm.
     */
    private RSAPrivateKey loadPrivateKey(
            KeyStore keyStore,
            char[] privateKeyPassword
    ) throws GeneralSecurityException {

        Key key =
                keyStore.getKey(
                        properties.keyAlias(),
                        privateKeyPassword
                );

        if (key == null) {
            throw new IllegalStateException(
                    """
                    No private key exists for JWT alias: %s
                    """.formatted(
                            properties.keyAlias()
                    )
            );
        }

        if (!(key instanceof RSAPrivateKey rsaPrivateKey)) {
            throw new IllegalStateException(
                    """
                    The JWT private key must be an RSA private key
                    """
            );
        }

        return rsaPrivateKey;
    }


    /*
     * A certificate stored in the keystore contains
     * the public half of the RSA key pair.
     */
    private RSAPublicKey loadPublicKey(
            KeyStore keyStore
    ) throws GeneralSecurityException {

        Certificate certificate =
                keyStore.getCertificate(
                        properties.keyAlias()
                );

        if (certificate == null) {
            throw new IllegalStateException(
                    """
                    No certificate exists for JWT alias: %s
                    """.formatted(
                            properties.keyAlias()
                    )
            );
        }

        if (!(
                certificate.getPublicKey()
                        instanceof RSAPublicKey rsaPublicKey
        )) {
            throw new IllegalStateException(
                    """
                    The JWT certificate must contain an RSA public key
                    """
            );
        }

        return rsaPublicKey;
    }
}