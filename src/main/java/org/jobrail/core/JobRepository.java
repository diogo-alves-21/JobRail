/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {

    Optional<Job> findByIdOptional(UUID id);

    List<Job> listJobsByStatus(JobStatus status);

    Job store(Job job);

    List<Job> process(List<Job> jobs);

    void markSucceeded(UUID id, int attempts);

    void markFailed(UUID id);
}
