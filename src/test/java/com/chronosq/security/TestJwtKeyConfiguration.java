package com.chronosq.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import java.util.UUID;

import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.boot.test.context.TestConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;


/*
 * Test-only RSA key configuration.
 *
 * Tests must not depend on:
 *
 * - the production .p12 file
 * - production passwords
 * - production signing keys
 *
 * Therefore, this configuration generates a temporary
 * RSA key every time the test application starts.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestJwtKeyConfiguration {

    /*
     * Generate a temporary 2048-bit RSA key pair.
     *
     * Production uses 3072 bits, but 2048 bits is sufficient
     * for tests and makes the test suite faster.
     */
    @Bean
    RSAKey testRsaKey() throws Exception {

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "RSA"
                );

        generator.initialize(2_048);

        KeyPair keyPair =
                generator.generateKeyPair();

        RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(
                        "test-"
                                + UUID.randomUUID()
                )
                .build();
    }


    /*
     * Replace JwtKeyStoreLoader with a test double.
     *
     * Production:
     * JwtKeyStoreLoader reads the .p12 file.
     *
     * Test:
     * It returns the temporary in-memory RSA key.
     */
    @Bean
    @Primary
    JwtKeyStoreLoader testJwtKeyStoreLoader(
            RSAKey testRsaKey
    ) {

        JwtKeyStoreLoader loader =
                mock(JwtKeyStoreLoader.class);

        when(loader.loadRsaKey())
                .thenReturn(testRsaKey);

        return loader;
    }
}