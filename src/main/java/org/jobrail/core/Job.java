package org.jobrail.core;

import java.time.Instant;
import java.util.UUID;

public record Job(UUID id, String type, String payload, JobStatus status, int currentAttempts,
                  int maxAttempts, Instant runAfter, Instant createdAt, Instant updatedAt) { }
