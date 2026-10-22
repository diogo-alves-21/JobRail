/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core.queries;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.Job;
import org.jobrail.core.JobRepository;
import org.jobrail.core.exceptions.JobNotFoundException;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class GetJob {

    private final JobRepository jobRepository;

    public Job query(UUID id) {
        return jobRepository.findByIdOptional(id).orElseThrow((JobNotFoundException::new));
    }
}
