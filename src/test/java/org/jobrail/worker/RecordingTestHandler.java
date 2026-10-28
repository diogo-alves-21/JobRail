/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.worker;

import jakarta.enterprise.context.ApplicationScoped;
import org.jobrail.core.Job;
import org.jobrail.core.JobHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic handler for tests: always succeeds and records how many times each job id was handled. The real Email
 * and Payment handlers fail at random, which would make retries (and the backoff delays) leak into the test.
 */
@ApplicationScoped
public class RecordingTestHandler implements JobHandler {

    static final Map<UUID, AtomicInteger> CALLS = new ConcurrentHashMap<>();

    @Override
    public String supportedType() {
        return "TESTRECORD";
    }

    @Override
    public void handleJob(Job job) {
        CALLS.computeIfAbsent(job.id(), id -> new AtomicInteger()).incrementAndGet();
    }

    static void reset() {
        CALLS.clear();
    }
}
