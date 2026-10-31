/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.worker;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class WorkersEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("jobrail.workers.enabled", "true", "jobrail.workers.count", "3", "jobrail.workers.poll-interval",
                "100ms");
    }
}
