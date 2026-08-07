package com.chronosq.metrics;

import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class ChronosQInfoContributor
        implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {

        builder.withDetail(
                "chronosq",
                Map.of(
                        "application",
                        "ChronosQ",
                        "description",
                        "Persistent distributed job scheduler",
                        "storage",
                        "PostgreSQL",
                        "deliveryGuarantee",
                        "AT_LEAST_ONCE",
                        "scheduling",
                        Map.of(
                                "immediate",
                                true,
                                "oneTime",
                                true,
                                "fixedInterval",
                                true
                        ),
                        "reliability",
                        Map.of(
                                "retries",
                                true,
                                "workerLeases",
                                true,
                                "expiredLeaseRecovery",
                                true,
                                "deadLettering",
                                true
                        )
                )
        );
    }
}

//This class provides static metadata about the ChronosQ application to Spring Boot Actuator's /info endpoint.
//The exposed details include the application name, its description as a "Persistent distributed job scheduler",
// its use of "PostgreSQL" for storage, and an "AT_LEAST_ONCE" delivery guarantee.
// It also exposes boolean maps indicating supported scheduling types (immediate, oneTime, fixedInterval)
// and reliability features (retries, workerLeases, expiredLeaseRecovery, deadLettering).