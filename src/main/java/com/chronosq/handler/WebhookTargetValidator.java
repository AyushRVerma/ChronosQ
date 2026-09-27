package com.chronosq.handler;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class WebhookTargetValidator {

    private final HostResolver hostResolver;

    public WebhookTargetValidator() {
        this(InetAddress::getAllByName);
    }

    WebhookTargetValidator(HostResolver hostResolver) {
        this.hostResolver = Objects.requireNonNull(hostResolver);
    }

    public ValidatedWebhookTarget validate(URI target) {
        Objects.requireNonNull(target, "target must not be null");
        String host = target.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(
                    "Webhook URL must contain a valid host"
            );
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")) {
            throw unsafeTarget(host);
        }

        try {
            InetAddress[] addresses =
                    hostResolver.resolve(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException(
                        "Webhook host did not resolve to an address"
                );
            }

            for (InetAddress address : addresses) {
                if (isNonPublic(address)) {
                    throw unsafeTarget(host);
                }
            }

            return new ValidatedWebhookTarget(target, addresses);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(
                    "Webhook host could not be resolved",
                    exception
            );
        }
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public record ValidatedWebhookTarget(
            URI uri,
            InetAddress[] addresses
    ) {
        public ValidatedWebhookTarget {
            Objects.requireNonNull(uri, "uri must not be null");
            Objects.requireNonNull(addresses, "addresses must not be null");
            if (addresses.length == 0) {
                throw new IllegalArgumentException(
                        "addresses must not be empty"
                );
            }
            addresses = Arrays.copyOf(addresses, addresses.length);
        }

        @Override
        public InetAddress[] addresses() {
            return Arrays.copyOf(addresses, addresses.length);
        }
    }

    private boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);

            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 0)
                    || (first == 192 && second == 168)
                    || (first == 198 && (second == 18 || second == 19))
                    || first >= 224;
        }

        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return (first & 0xfe) == 0xfc
                || (first == 0x20 && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8);
    }

    private IllegalArgumentException unsafeTarget(String host) {
        return new IllegalArgumentException(
                "Webhook host is not a permitted public target: " + host
        );
    }
}
