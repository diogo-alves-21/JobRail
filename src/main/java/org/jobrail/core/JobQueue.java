/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class JobQueue {

    private final JobRepository jobRepository;

    public UUID enqueue(String type, String payload, int maxAttempts) {
        Job job = jobRepository.store(Job.newPending(type, payload, maxAttempts));
        return job.id();
    }
}
