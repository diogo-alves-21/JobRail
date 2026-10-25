/*
* Copyright (c) 2026 Diogo Alves
* JobRail - Distributed Job Queue
* All rights reserved.
*/
package org.jobrail.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jobrail.core.exceptions.RequeueJobException;
import org.jobrail.core.jobstatuses.Dead;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class JobRequeue {

    private final JobRepository jobRepository;

    @Transactional
    public UUID requeue(UUID id) {
        jobRepository.findByIdAndStatusOptional(id, Dead.getInstance()).orElseThrow(() -> new RequeueJobException(id));
        jobRepository.updateForRequeue(id);
        return id;
    }
}
