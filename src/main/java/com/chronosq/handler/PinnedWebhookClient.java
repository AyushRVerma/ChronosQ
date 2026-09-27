package com.chronosq.handler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.chronosq.configuration.WebhookProperties;
import com.chronosq.handler.WebhookTargetValidator.ValidatedWebhookTarget;

@Component
public class PinnedWebhookClient {

    private final WebhookProperties properties;

    public PinnedWebhookClient(WebhookProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public int exchange(
            ValidatedWebhookTarget target,
            HttpWebhookPayload payload
    ) throws Exception {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new PinnedDnsResolver(
                                target.uri().getHost(),
                                target.addresses()
                        ))
                        .setDefaultConnectionConfig(
                                ConnectionConfig.custom()
                                        .setConnectTimeout(
                                                properties.connectTimeoutMs(),
                                                TimeUnit.MILLISECONDS
                                        )
                                        .setSocketTimeout(
                                                properties.readTimeoutMs(),
                                                TimeUnit.MILLISECONDS
                                        )
                                        .build()
                        )
                        .setMaxConnTotal(1)
                        .setMaxConnPerRoute(1)
                        .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build()) {
            HttpComponentsClientHttpRequestFactory requestFactory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            RestClient.RequestBodySpec request = RestClient.builder()
                    .requestFactory(requestFactory)
                    .build()
                    .method(payload.method())
                    .uri(target.uri())
                    .headers(headers -> payload.headers().forEach(
                            headers::set
                    ));

            if (payload.body() != null && !payload.body().isNull()) {
                request.contentType(MediaType.APPLICATION_JSON)
                        .body(payload.body());
            }

            return request.exchange((httpRequest, httpResponse) ->
                    httpResponse.getStatusCode().value()
            );
        }
    }

    static final class PinnedDnsResolver implements DnsResolver {

        private final String host;
        private final InetAddress[] addresses;

        PinnedDnsResolver(String host, InetAddress[] addresses) {
            this.host = Objects.requireNonNull(host)
                    .toLowerCase(Locale.ROOT);
            this.addresses = Arrays.copyOf(
                    Objects.requireNonNull(addresses),
                    addresses.length
            );
        }

        @Override
        public InetAddress[] resolve(String requestedHost)
                throws UnknownHostException {
            if (!host.equals(requestedHost.toLowerCase(Locale.ROOT))) {
                throw new UnknownHostException(
                        "DNS resolution is not permitted for " + requestedHost
                );
            }
            return Arrays.copyOf(addresses, addresses.length);
        }

        @Override
        public String resolveCanonicalHostname(String requestedHost)
                throws UnknownHostException {
            if (!host.equals(requestedHost.toLowerCase(Locale.ROOT))) {
                throw new UnknownHostException(
                        "DNS resolution is not permitted for " + requestedHost
                );
            }
            return host;
        }
    }
}
