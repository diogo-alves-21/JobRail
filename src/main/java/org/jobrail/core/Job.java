package org.jobrail.core;

import org.jobrail.core.jobstatuses.Pending;

import java.time.Instant;
import java.util.UUID;

public record Job(UUID id, String type, String payload, JobStatus status, int currentAttempts,
                  int maxAttempts, Instant runAfter, Instant createdAt, Instant updatedAt) {

    public static Job newPending(String type, String payload, int maxAttempts){
        return new Job(null, type, payload, Pending.provider(), 0, maxAttempts, Instant.now(), Instant.now(), Instant.now());
    }
}
