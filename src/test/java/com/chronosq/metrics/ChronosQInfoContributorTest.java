package com.chronosq.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;

class ChronosQInfoContributorTest {

    @Test
    void shouldAddChronosQApplicationInformation() {
        ChronosQInfoContributor contributor =
                new ChronosQInfoContributor();

        Info.Builder builder =
                new Info.Builder();

        contributor.contribute(builder);

        Info info = builder.build();

        assertThat(info.getDetails())
                .containsKey("chronosq");

        Object chronosQDetail =
                info.getDetails().get("chronosq");

        assertThat(chronosQDetail)
                .isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> chronosQ =
                (Map<String, Object>) chronosQDetail;

        assertThat(chronosQ)
                .containsEntry(
                        "application",
                        "ChronosQ"
                )
                .containsEntry(
                        "storage",
                        "PostgreSQL"
                )
                .containsEntry(
                        "deliveryGuarantee",
                        "AT_LEAST_ONCE"
                );
    }
}