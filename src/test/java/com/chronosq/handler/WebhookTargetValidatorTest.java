package com.chronosq.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class WebhookTargetValidatorTest {

    private final WebhookTargetValidator validator =
            new WebhookTargetValidator();

    @Test
    void shouldRejectLoopbackHostname() {
        assertThatThrownBy(
                () -> validator.validate(
                        URI.create("http://localhost/admin")
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPrivateIpv4Address() {
        assertThatThrownBy(
                () -> validator.validate(
                        URI.create("http://10.0.0.1/admin")
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectCloudMetadataAddress() {
        assertThatThrownBy(
                () -> validator.validate(
                        URI.create("http://169.254.169.254/latest/meta-data")
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPinTheValidatedResolutionAgainstDnsRebinding()
            throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(
                new byte[] {93, (byte) 184, (byte) 216, 34}
        );
        InetAddress privateAddress = InetAddress.getByAddress(
                new byte[] {10, 0, 0, 1}
        );
        AtomicInteger resolutions = new AtomicInteger();
        WebhookTargetValidator rebindingValidator =
                new WebhookTargetValidator(host ->
                        resolutions.getAndIncrement() == 0
                                ? new InetAddress[] {publicAddress}
                                : new InetAddress[] {privateAddress}
                );

        WebhookTargetValidator.ValidatedWebhookTarget target =
                rebindingValidator.validate(
                        URI.create("https://example.com/webhook")
                );
        PinnedWebhookClient.PinnedDnsResolver pinnedResolver =
                new PinnedWebhookClient.PinnedDnsResolver(
                        target.uri().getHost(),
                        target.addresses()
                );

        assertThat(pinnedResolver.resolve("example.com"))
                .containsExactly(publicAddress);
        assertThat(pinnedResolver.resolve("EXAMPLE.COM"))
                .containsExactly(publicAddress);
        assertThat(resolutions).hasValue(1);
    }
}
