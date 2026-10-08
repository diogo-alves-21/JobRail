/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {

    Optional<Job> findByIdOptional(UUID id);

    List<Job> listJobsByStatus(JobStatus status);

    Job store(Job job);

    List<Job> claim(List<Job> jobs);

    void markSucceeded(UUID id);

    void reschedule(UUID id, Instant runAfter);

    boolean rescheduleIfStuck(UUID id, Instant seenUpdatedAt, Instant runAfter);

    void markDead(UUID id);

    boolean markDeadIfStuck(UUID id, Instant seenUpdatedAt);

    int updateAttempts(UUID id);
}
