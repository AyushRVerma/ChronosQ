package com.chronosq.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chronosq.security.dashboard-client")
public record DashboardOAuthClientProperties(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,99}$")
        String clientId,
        @NotBlank
        @Size(min = 32, max = 200)
        String clientSecret
) {
    @Override
    public String toString() {
        return "DashboardOAuthClientProperties[clientId=" + clientId
                + ", clientSecret=<redacted>]";
    }
}
