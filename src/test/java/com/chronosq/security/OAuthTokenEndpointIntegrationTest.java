package com.chronosq.security;
import org.springframework.boot.flyway.autoconfigure
        .FlywayAutoConfiguration;

import org.springframework.boot.jdbc.autoconfigure
        .DataSourceAutoConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.httpBasic;

import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.webmvc.test.autoconfigure
        .AutoConfigureMockMvc;

import org.springframework.context.annotation.Import;

import org.springframework.http.MediaType;

import org.springframework.security.oauth2.jose.jws
        .SignatureAlgorithm;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


/*
 * Tests the real OAuth2 token endpoint.
 *
 * This verifies:
 *
 * - Client Credentials authentication
 * - client secret validation
 * - requested scopes
 * - JWT signing
 * - JWT audience customization
 */
@SpringBootTest(
        classes = {
                OAuthTokenEndpointIntegrationTest
                        .TestApplication.class,
                TestJwtKeyConfiguration.class
        },
        properties = {
                """
                chronosq.security.oauth-client.client-id=\
                test-chronosq-client
                """,

                """
                chronosq.security.oauth-client.client-secret=\
                Test-Client-Secret-With-More-Than-32-Characters
                """,

                """
                chronosq.security.oauth-client.scopes=\
                jobs.submit,jobs.read,jobs.cancel,jobs.retry,metrics.read
                """,

                """
                chronosq.security.oauth-client.\
                access-token-time-to-live=10m
                """,

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
class OAuthTokenEndpointIntegrationTest {

    private static final String CLIENT_ID =
            "test-chronosq-client";

    private static final String CLIENT_SECRET =
            """
            Test-Client-Secret-With-More-Than-32-Characters
            """.trim();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RSAKey testRsaKey;


    /*
     * Minimal test application containing only the
     * Authorization Server components.
     *
     * It does not start the database, scheduler or workers.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(
            exclude = {
                    DataSourceAutoConfiguration.class,
                    FlywayAutoConfiguration.class
            }
    )
    @Import({
            AuthorizationServerConfiguration.class,
            ChronosQTokenCustomizer.class
    })
    static class TestApplication {
    }


    @Test
    void shouldIssueSignedAccessTokenForValidClient()
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                post("/oauth2/token")

                                        /*
                                         * Creates:
                                         *
                                         * Authorization:
                                         * Basic base64(clientId:secret)
                                         */
                                        .with(
                                                httpBasic(
                                                        CLIENT_ID,
                                                        CLIENT_SECRET
                                                )
                                        )

                                        .contentType(
                                                MediaType
                                                        .APPLICATION_FORM_URLENCODED
                                        )

                                        .param(
                                                "grant_type",
                                                "client_credentials"
                                        )

                                        .param(
                                                "scope",
                                                "jobs.submit jobs.read"
                                        )
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                jsonPath("$.access_token")
                                        .isString()
                        )
                        .andExpect(
                                jsonPath("$.token_type")
                                        .value("Bearer")
                        )
                        .andExpect(
                                jsonPath("$.expires_in")
                                        .isNumber()
                        )
                        .andReturn();

        JsonNode responseBody =
                objectMapper.readTree(
                        result.getResponse()
                                .getContentAsString()
                );

        String encodedAccessToken =
                responseBody
                        .get("access_token")
                        .asText();

        /*
         * Build a decoder using only the public key.
         *
         * Successfully decoding the token proves that
         * it was signed by the connected private key.
         */
        JwtDecoder decoder =
                NimbusJwtDecoder
                        .withPublicKey(
                                testRsaKey
                                        .toRSAPublicKey()
                        )
                        .signatureAlgorithm(
                                SignatureAlgorithm.RS256
                        )
                        .build();

        Jwt jwt =
                decoder.decode(
                        encodedAccessToken
                );

        assertThat(jwt.getAudience())
                .containsExactly(
                        "chronosq-api"
                );

        assertThat(
                jwt.getClaimAsStringList("scope")
        ).containsExactlyInAnyOrder(
                "jobs.submit",
                "jobs.read"
        );

        assertThat(jwt.getSubject())
                .isEqualTo(CLIENT_ID);
    }


    @Test
    void shouldRejectInvalidClientSecret()
            throws Exception {

        mockMvc.perform(
                        post("/oauth2/token")
                                .with(
                                        httpBasic(
                                                CLIENT_ID,
                                                "wrong-client-secret"
                                        )
                                )
                                .contentType(
                                        MediaType
                                                .APPLICATION_FORM_URLENCODED
                                )
                                .param(
                                        "grant_type",
                                        "client_credentials"
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("invalid_client")
                );
    }


    @Test
    void shouldRejectScopeNotRegisteredForClient()
            throws Exception {

        mockMvc.perform(
                        post("/oauth2/token")
                                .with(
                                        httpBasic(
                                                CLIENT_ID,
                                                CLIENT_SECRET
                                        )
                                )
                                .contentType(
                                        MediaType
                                                .APPLICATION_FORM_URLENCODED
                                )
                                .param(
                                        "grant_type",
                                        "client_credentials"
                                )
                                .param(
                                        "scope",
                                        "jobs.admin"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("invalid_scope")
                );
    }
}